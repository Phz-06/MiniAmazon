package edu.ece568.MiniAmazon.fulfillment;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.proto.Events.ConfirmLoaded;
import edu.ece568.MiniAmazon.proto.Events.Item;
import edu.ece568.MiniAmazon.proto.Events.LoadPackage;
import edu.ece568.MiniAmazon.proto.Events.PackOrder;
import edu.ece568.MiniAmazon.proto.Events.RequestPickup;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import edu.ece568.MiniAmazon.shared.messaging.EventOutbox;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The path of a paid order through the warehouse and onto a truck:
 *
 *   PAID -> PACKING -> PACKED -> LOADING -> LOADED -> DELIVERING -> DELIVERED
 *
 * This service decides; the warehouse gateway and the carrier do. Each step is
 * triggered by an event from one of them and answered with a command to the
 * other, and has the same shape in one transaction: move the status by
 * compare-and-set, and if (and only if) that succeeded, put the next command
 * in the outbox.
 *
 * So a step happens completely or not at all, and a trigger that arrives
 * twice finds the status already moved and does nothing. The steps of one
 * order cannot overtake each other either: the command for step n+1 does not
 * exist until the event for step n has been applied.
 */
@Slf4j
@Service
public class FulfillmentService {

    /** The one warehouse this application runs. */
    public static final int WAREHOUSE_ID = 1;

    private final OrderRepository orders;
    private final EventOutbox outbox;

    public FulfillmentService(OrderRepository orders, EventOutbox outbox) {
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
        PackOrder.Builder pack = PackOrder.newBuilder().setOrderId(orderId).setWarehouseId(WAREHOUSE_ID);
        for (OrderItem item : order.getOrderItems()) {
            pack.addItems(Item.newBuilder()
                    .setProductId(Long.parseLong(item.getProduct().getProductId()))
                    .setDescription(item.getProduct().getName())
                    .setCount(item.getQuantity()));
        }
        outbox.publish(Topics.WAREHOUSE_COMMANDS, key(orderId), e -> e.setPackOrder(pack));
    }

    /** Warehouse: the package is packed. Ask the carrier for a truck. */
    @Transactional
    public void onPacked(long orderId) {
        if (orders.transition(orderId, Status.PACKING, Status.PACKED) == 0) {
            log.info("Ignoring 'packed' for order {}: it is not being packed", orderId);
            return;
        }
        Order order = orders.findById(orderId).orElseThrow();
        RequestPickup.Builder pickup = RequestPickup.newBuilder().setOrderId(orderId).setWarehouseId(WAREHOUSE_ID)
                .setDestX(order.getDeliveryX()).setDestY(order.getDeliveryY());
        if (order.getUpsAccountName() != null) {
            pickup.setAccount(order.getUpsAccountName());
        }
        outbox.publish(Topics.CARRIER_COMMANDS, key(orderId), e -> e.setRequestPickup(pickup));
    }

    /** Carrier: a truck is standing at the warehouse. Ask the warehouse to load the package onto it. */
    @Transactional
    public void onTruckArrived(long orderId, int truckId) {
        if (orders.transition(orderId, Status.PACKED, Status.LOADING) == 0) {
            log.info("Ignoring truck {} for order {}: it is not waiting for a truck", truckId, orderId);
            return;
        }
        orders.assignTruck(orderId, truckId);
        outbox.publish(Topics.WAREHOUSE_COMMANDS, key(orderId), e -> e.setLoadPackage(
                LoadPackage.newBuilder().setOrderId(orderId).setWarehouseId(WAREHOUSE_ID).setTruckId(truckId)));
    }

    /** Warehouse: the package is on the truck. Tell the carrier it can leave. */
    @Transactional
    public void onLoaded(long orderId) {
        if (orders.transition(orderId, Status.LOADING, Status.LOADED) == 0) {
            log.info("Ignoring 'loaded' for order {}: it is not being loaded", orderId);
            return;
        }
        Order order = orders.findById(orderId).orElseThrow();
        outbox.publish(Topics.CARRIER_COMMANDS, key(orderId), e -> e.setConfirmLoaded(
                ConfirmLoaded.newBuilder().setOrderId(orderId).setTruckId(order.getTruckId())));
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

    /** All records about one order share a key, hence a partition, hence an order of arrival. */
    private static String key(long orderId) {
        return "order-" + orderId;
    }
}
