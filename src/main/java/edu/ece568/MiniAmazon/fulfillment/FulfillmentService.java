package edu.ece568.MiniAmazon.fulfillment;

import edu.ece568.MiniAmazon.carrier.CarrierMessages;
import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import edu.ece568.MiniAmazon.world.WorldCommands;
import edu.ece568.MiniAmazon.world.WorldGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The path of a paid order through the warehouse and onto a truck:
 *
 *   PAID -> PACKING -> PACKED -> LOADING -> LOADED -> DELIVERED
 *
 * Every step has the same shape, in one transaction: move the status by
 * compare-and-set, and if (and only if) that succeeded, record the next
 * message in the outbox. So a step either happened completely or not at all,
 * and a trigger that arrives twice (a resent "packed", a duplicated truck
 * assignment) finds the status already moved and does nothing. The shipment
 * id used with the world and the carrier is the order id.
 */
@Slf4j
@Service
public class FulfillmentService {

    private final OrderRepository orders;
    private final Outbox outbox;

    public FulfillmentService(OrderRepository orders, Outbox outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /** Called from the payment transaction: paying and asking the warehouse to pack are one commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void startPacking(long orderId) {
        if (orders.transition(orderId, Status.PAID, Status.PACKING) == 0) {
            throw new IllegalStateException("Order " + orderId + " is not paid");
        }
        Order order = orders.findById(orderId).orElseThrow();
        List<AProduct> things = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            things.add(WorldCommands.product(Long.parseLong(item.getProduct().getProductId()),
                    item.getProduct().getName(), item.getQuantity()));
        }
        outbox.enqueue(Destination.WORLD, WorldCommands.PACK, orderId,
                seq -> WorldCommands.pack(seq, WorldGateway.WAREHOUSE_ID, orderId, things));
    }

    /** World: the package is packed. Ask the carrier for a truck. */
    @Transactional
    public void onPacked(long orderId) {
        if (orders.transition(orderId, Status.PACKING, Status.PACKED) == 0) {
            log.info("Ignoring 'packed' for order {}: it is not being packed", orderId);
            return;
        }
        Order order = orders.findById(orderId).orElseThrow();
        outbox.enqueue(Destination.CARRIER, CarrierMessages.REQUEST_PICKUP, orderId,
                seq -> CarrierMessages.requestPickup(orderId, WorldGateway.WAREHOUSE_ID,
                        order.getDeliveryX(), order.getDeliveryY(), order.getUpsAccountName()));
    }

    /** Carrier: this truck will take it. Ask the world to load the package onto it. */
    @Transactional
    public void onTruckAssigned(long orderId, int truckId) {
        if (orders.transition(orderId, Status.PACKED, Status.LOADING) == 0) {
            log.info("Ignoring truck {} for order {}: it is not waiting for a truck", truckId, orderId);
            return;
        }
        orders.assignTruck(orderId, truckId);
        outbox.enqueue(Destination.WORLD, WorldCommands.LOAD, orderId,
                seq -> WorldCommands.load(seq, WorldGateway.WAREHOUSE_ID, truckId, orderId));
    }

    /** World: the package is on the truck. Tell the carrier it can leave. */
    @Transactional
    public void onLoaded(long orderId) {
        if (orders.transition(orderId, Status.LOADING, Status.LOADED) == 0) {
            log.info("Ignoring 'loaded' for order {}: it is not being loaded", orderId);
            return;
        }
        Order order = orders.findById(orderId).orElseThrow();
        outbox.enqueue(Destination.CARRIER, CarrierMessages.CONFIRM_LOADED, orderId,
                seq -> CarrierMessages.confirmLoaded(orderId, order.getTruckId()));
    }

    /** Carrier: the truck left the warehouse. */
    @Transactional
    public void onOutForDelivery(long orderId) {
        orders.transition(orderId, Status.LOADED, Status.DELIVERING);
    }

    /** Carrier: delivered. */
    @Transactional
    public void onDelivered(long orderId) {
        if (orders.transition(orderId, Status.DELIVERING, Status.DELIVERED) == 0
                && orders.transition(orderId, Status.LOADED, Status.DELIVERED) == 0) {
            log.info("Ignoring 'delivered' for order {}: it is not on a truck", orderId);
        }
    }
}
