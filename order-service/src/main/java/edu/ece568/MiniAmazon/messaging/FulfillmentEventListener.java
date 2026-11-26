package edu.ece568.MiniAmazon.messaging;

import edu.ece568.MiniAmazon.fulfillment.FulfillmentService;
import edu.ece568.MiniAmazon.fulfillment.ReplenishmentService;
import edu.ece568.MiniAmazon.fulfillment.WorldBooks;
import edu.ece568.MiniAmazon.proto.Events.CommandRejected;
import edu.ece568.MiniAmazon.shared.messaging.IdempotentConsumer;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * What the warehouse gateway and the carrier report back. Every record is
 * applied at most once (IdempotentConsumer); a record we cannot apply is
 * retried and then dead-lettered by the container's error handler.
 */
@Slf4j
@Component
public class FulfillmentEventListener {

    private final IdempotentConsumer consumer;
    private final FulfillmentService fulfillment;
    private final ReplenishmentService replenishment;
    private final WorldBooks books;

    public FulfillmentEventListener(IdempotentConsumer consumer, FulfillmentService fulfillment,
                                    ReplenishmentService replenishment, WorldBooks books) {
        this.consumer = consumer;
        this.fulfillment = fulfillment;
        this.replenishment = replenishment;
        this.books = books;
    }

    @KafkaListener(topics = Topics.WAREHOUSE_EVENTS)
    public void fromWarehouse(ConsumerRecord<String, byte[]> record) {
        consumer.handle(record.value(), event -> {
            switch (event.getBodyCase()) {
                case WORLD_ADOPTED -> books.adopt(event.getWorldAdopted().getWorldId(), event.getWorldAdopted().getGeneration());
                case STOCK_ARRIVED -> replenishment.received(event.getStockArrived());
                case ORDER_PACKED -> fulfillment.onPacked(event.getOrderPacked().getOrderId());
                case PACKAGE_LOADED -> fulfillment.onLoaded(event.getPackageLoaded().getOrderId());
                case COMMAND_REJECTED -> rejected(event.getCommandRejected());
                default -> log.debug("Ignoring {} on {}", event.getBodyCase(), record.topic());
            }
        });
    }

    @KafkaListener(topics = Topics.CARRIER_EVENTS)
    public void fromCarrier(ConsumerRecord<String, byte[]> record) {
        consumer.handle(record.value(), event -> {
            switch (event.getBodyCase()) {
                case TRUCK_ARRIVED -> fulfillment.onTruckArrived(
                        event.getTruckArrived().getOrderId(), event.getTruckArrived().getTruckId());
                case OUT_FOR_DELIVERY -> fulfillment.onOutForDelivery(event.getOutForDelivery().getOrderId());
                case DELIVERED -> fulfillment.onDelivered(event.getDelivered().getOrderId());
                default -> log.debug("Ignoring {} on {}", event.getBodyCase(), record.topic());
            }
        });
    }

    /** The warehouse refused a command. The order stays where it is; this needs a person. */
    private void rejected(CommandRejected rejection) {
        log.error("Warehouse rejected {} for order {}: {}", rejection.getCommand(), rejection.getOrderId(), rejection.getReason());
    }
}
