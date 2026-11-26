package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.Events.Delivered;
import edu.ece568.MiniAmazon.proto.Events.OutForDelivery;
import edu.ece568.MiniAmazon.proto.Events.RequestPickup;
import edu.ece568.MiniAmazon.proto.Events.TruckArrived;
import edu.ece568.MiniAmazon.shared.messaging.EventOutbox;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * A shipment's life:
 *
 *   REQUESTED -> TRUCK_EN_ROUTE -> TRUCK_ARRIVED -> OUT_FOR_DELIVERY -> DELIVERED
 *
 * and a truck's: IDLE -> TO_WAREHOUSE -> AT_WAREHOUSE -> DELIVERING -> IDLE.
 *
 * Every transition is a single UPDATE guarded by the status it expects, and
 * the message that follows is written in the same transaction. A request or
 * a world report that arrives twice updates nothing the second time and so
 * sends nothing.
 */
@Service
public class DispatchService {
    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final JdbcClient jdbc;
    private final WorldCommandStore commands;
    private final EventOutbox outbox;

    public DispatchService(JdbcClient jdbc, WorldCommandStore commands, EventOutbox outbox) {
        this.jdbc = jdbc;
        this.commands = commands;
        this.outbox = outbox;
    }

    /** The package id is the primary key, so the same request twice is one shipment. */
    @Transactional
    public void requestPickup(RequestPickup request) {
        jdbc.sql("""
                        INSERT INTO shipment (order_id, warehouse_id, dest_x, dest_y)
                        VALUES (:orderId, :warehouseId, :x, :y)
                        ON CONFLICT (order_id) DO NOTHING""")
                .param("orderId", request.getOrderId()).param("warehouseId", request.getWarehouseId())
                .param("x", request.getDestX()).param("y", request.getDestY())
                .update();
        dispatch();
    }

    /**
     * Pairs waiting packages with idle trucks, oldest package first.
     *
     * Both sides are picked with FOR UPDATE SKIP LOCKED. Two dispatchers
     * running at once (a second instance, or the scheduler and a request)
     * each lock different rows instead of waiting for, or double-booking,
     * the same truck. With no idle truck the package simply stays REQUESTED
     * until one comes back.
     */
    @Transactional
    public int dispatch() {
        int assigned = 0;
        while (true) {
            Optional<Map<String, Object>> waiting = jdbc.sql("""
                            SELECT order_id, warehouse_id FROM shipment
                             WHERE status = 'REQUESTED'
                             ORDER BY requested_at, order_id
                             LIMIT 1 FOR UPDATE SKIP LOCKED""").query().listOfRows().stream().findFirst();
            if (waiting.isEmpty()) {
                return assigned;
            }
            Optional<Integer> idle = jdbc.sql("""
                            SELECT id FROM truck WHERE status = 'IDLE'
                             ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED""").query(Integer.class).optional();
            if (idle.isEmpty()) {
                return assigned;
            }
            long orderId = ((Number) waiting.get().get("order_id")).longValue();
            int warehouseId = ((Number) waiting.get().get("warehouse_id")).intValue();
            int truckId = idle.get();
            jdbc.sql("UPDATE truck SET status = 'TO_WAREHOUSE' WHERE id = :id").param("id", truckId).update();
            jdbc.sql("UPDATE shipment SET status = 'TRUCK_EN_ROUTE', truck_id = :truck WHERE order_id = :orderId")
                    .param("truck", truckId).param("orderId", orderId).update();
            commands.enqueue(UpsProtocol.PICKUP, orderId, seq -> UpsProtocol.pickup(seq, truckId, warehouseId));
            log.info("Truck {} sent to warehouse {} for package {}", truckId, warehouseId, orderId);
            assigned++;
        }
    }

    /** World: a truck finished what it was doing. */
    @Transactional
    public void truckFinished(int truckId, String status) {
        if (UpsProtocol.ARRIVED_AT_WAREHOUSE.equals(status)) {
            jdbc.sql("UPDATE truck SET status = 'AT_WAREHOUSE' WHERE id = :id AND status = 'TO_WAREHOUSE'")
                    .param("id", truckId).update();
            Optional<Long> orderId = jdbc.sql("""
                            UPDATE shipment SET status = 'TRUCK_ARRIVED'
                             WHERE truck_id = :truck AND status = 'TRUCK_EN_ROUTE'
                            RETURNING order_id""").param("truck", truckId).query(Long.class).optional();
            orderId.ifPresent(id -> outbox.publish(Topics.CARRIER_EVENTS, key(id),
                    e -> e.setTruckArrived(TruckArrived.newBuilder().setOrderId(id).setTruckId(truckId))));
        } else if (UpsProtocol.IDLE.equals(status)) {
            int freed = jdbc.sql("UPDATE truck SET status = 'IDLE' WHERE id = :id AND status = 'DELIVERING'")
                    .param("id", truckId).update();
            if (freed == 1) {
                dispatch();
            }
        }
    }

    /** Warehouse (through order-service): the package is on our truck. Send it out. */
    @Transactional
    public void confirmLoaded(long orderId) {
        Optional<Map<String, Object>> shipment = jdbc.sql("""
                        UPDATE shipment SET status = 'OUT_FOR_DELIVERY'
                         WHERE order_id = :orderId AND status = 'TRUCK_ARRIVED'
                        RETURNING truck_id, dest_x, dest_y""").param("orderId", orderId)
                .query().listOfRows().stream().findFirst();
        if (shipment.isEmpty()) {
            log.info("Ignoring 'loaded' for package {}: no truck is waiting for it", orderId);
            return;
        }
        int truckId = ((Number) shipment.get().get("truck_id")).intValue();
        int x = ((Number) shipment.get().get("dest_x")).intValue();
        int y = ((Number) shipment.get().get("dest_y")).intValue();
        jdbc.sql("UPDATE truck SET status = 'DELIVERING' WHERE id = :id").param("id", truckId).update();
        commands.enqueue(UpsProtocol.DELIVER, orderId, seq -> UpsProtocol.deliver(seq, truckId, orderId, x, y));
        outbox.publish(Topics.CARRIER_EVENTS, key(orderId),
                e -> e.setOutForDelivery(OutForDelivery.newBuilder().setOrderId(orderId)));
    }

    /** World: the package was handed over. */
    @Transactional
    public void delivered(long orderId) {
        int updated = jdbc.sql("""
                        UPDATE shipment SET status = 'DELIVERED', delivered_at = now()
                         WHERE order_id = :orderId AND status = 'OUT_FOR_DELIVERY'""")
                .param("orderId", orderId).update();
        if (updated == 1) {
            outbox.publish(Topics.CARRIER_EVENTS, key(orderId),
                    e -> e.setDelivered(Delivered.newBuilder().setOrderId(orderId)));
        }
    }

    /**
     * The gateway moved to another world. Our trucks do not exist there yet
     * and unfinished shipments belong to a world we can no longer reach.
     */
    @Transactional
    public void adoptWorld(long worldId, long generation, int trucks) {
        long known = jdbc.sql("SELECT generation FROM world_session WHERE id = 1").query(Long.class).single();
        if (generation <= known) {
            return; // an announcement we already followed
        }
        jdbc.sql("""
                        UPDATE world_session
                           SET world_id = :worldId, generation = :generation, trucks_registered = false, updated_at = now()
                         WHERE id = 1""")
                .param("worldId", worldId).param("generation", generation).update();
        jdbc.sql("DELETE FROM world_inbox").update();
        commands.failAllPending("Superseded: now in world " + worldId);
        jdbc.sql("UPDATE shipment SET status = 'ABANDONED' WHERE status <> 'DELIVERED'").update();
        for (int id = 1; id <= trucks; id++) {
            jdbc.sql("INSERT INTO truck (id) VALUES (:id) ON CONFLICT (id) DO UPDATE SET status = 'IDLE'")
                    .param("id", id).update();
        }
        log.info("Moving to world {} (generation {}) with {} trucks", worldId, generation, trucks);
    }

    private static String key(long orderId) {
        return "order-" + orderId;
    }
}
