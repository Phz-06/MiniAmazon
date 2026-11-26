package edu.ece568.MiniAmazon.gateway;

import edu.ece568.MiniAmazon.proto.Events.CommandRejected;
import edu.ece568.MiniAmazon.proto.Events.Item;
import edu.ece568.MiniAmazon.proto.Events.OrderPacked;
import edu.ece568.MiniAmazon.proto.Events.PackageLoaded;
import edu.ece568.MiniAmazon.proto.Events.StockArrived;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AErr;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ALoaded;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APackage;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APacked;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import edu.ece568.MiniAmazon.shared.messaging.EventOutbox;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import edu.ece568.MiniAmazon.shared.worldlink.StoredCommand;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import edu.ece568.MiniAmazon.shared.worldlink.WorldInbox;
import edu.ece568.MiniAmazon.shared.worldlink.WorldLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what the world reports into events on Kafka.
 *
 * For each message, one transaction writes the inbox row and the outgoing
 * event together. The message is acknowledged to the world only after that
 * commit. A redelivery finds the inbox row and publishes nothing, so each
 * thing the world did becomes exactly one event, however often the world
 * repeats itself.
 */
@Component
public class WorldEventPublisher implements WorldLink.Listener<AResponses> {
    private static final Logger log = LoggerFactory.getLogger(WorldEventPublisher.class);

    private final WorldInbox inbox;
    private final EventOutbox outbox;
    private final WorldCommandStore commands;
    private final TransactionTemplate tx;

    public WorldEventPublisher(WorldInbox inbox, EventOutbox outbox, WorldCommandStore commands, TransactionTemplate tx) {
        this.inbox = inbox;
        this.outbox = outbox;
        this.commands = commands;
        this.tx = tx;
    }

    @Override
    public List<Long> onResponses(long worldId, AResponses responses) {
        List<Long> applied = new ArrayList<>();
        for (APurchaseMore arrived : responses.getArrivedList()) {
            if (once(worldId, arrived.getSeqnum(), "ARRIVED", () -> stockArrived(arrived))) {
                applied.add(arrived.getSeqnum());
            }
        }
        for (APacked packed : responses.getReadyList()) {
            if (once(worldId, packed.getSeqnum(), "PACKED", () -> outbox.publish(Topics.WAREHOUSE_EVENTS,
                    key(packed.getShipid()), e -> e.setOrderPacked(OrderPacked.newBuilder().setOrderId(packed.getShipid()))))) {
                applied.add(packed.getSeqnum());
            }
        }
        for (ALoaded loaded : responses.getLoadedList()) {
            if (once(worldId, loaded.getSeqnum(), "LOADED", () -> outbox.publish(Topics.WAREHOUSE_EVENTS,
                    key(loaded.getShipid()), e -> e.setPackageLoaded(PackageLoaded.newBuilder().setOrderId(loaded.getShipid()))))) {
                applied.add(loaded.getSeqnum());
            }
        }
        for (AErr error : responses.getErrorList()) {
            if (once(worldId, error.getSeqnum(), "ERROR", () -> rejected(error))) {
                applied.add(error.getSeqnum());
            }
        }
        for (APackage status : responses.getPackagestatusList()) {
            applied.add(status.getSeqnum());
        }
        return applied;
    }

    private void stockArrived(APurchaseMore arrived) {
        StockArrived.Builder event = StockArrived.newBuilder().setWarehouseId(arrived.getWhnum());
        for (AProduct thing : arrived.getThingsList()) {
            event.addItems(Item.newBuilder().setProductId(thing.getId()).setDescription(thing.getDescription()).setCount(thing.getCount()));
        }
        String key = arrived.getThingsCount() > 0 ? "product-" + arrived.getThings(0).getId() : "warehouse-" + arrived.getWhnum();
        outbox.publish(Topics.WAREHOUSE_EVENTS, key, e -> e.setStockArrived(event));
    }

    /** The world understood a command and refused it. Resending the same thing would be refused again. */
    private void rejected(AErr error) {
        log.error("World rejected command {}: {}", error.getOriginseqnum(), error.getErr());
        commands.markFailed(error.getOriginseqnum(), error.getErr());
        StoredCommand command = commands.find(error.getOriginseqnum()).orElse(null);
        long orderId = command == null || command.refId() == null ? 0 : command.refId();
        outbox.publish(Topics.WAREHOUSE_EVENTS, key(orderId), e -> e.setCommandRejected(CommandRejected.newBuilder()
                .setCommand(command == null ? "UNKNOWN" : command.type()).setOrderId(orderId).setReason(error.getErr())));
    }

    private boolean once(long worldId, long seqnum, String kind, Runnable effect) {
        try {
            tx.executeWithoutResult(status -> {
                if (inbox.firstTime(worldId, seqnum, kind)) {
                    effect.run();
                }
            });
            return true;
        } catch (RuntimeException e) {
            log.error("Could not apply world message {} ({}); leaving it unacknowledged", seqnum, kind, e);
            return false;
        }
    }

    private static String key(long orderId) {
        return "order-" + orderId;
    }
}
