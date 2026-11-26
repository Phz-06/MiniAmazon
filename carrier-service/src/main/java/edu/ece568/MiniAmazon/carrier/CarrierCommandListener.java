package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.shared.messaging.IdempotentConsumer;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CarrierCommandListener {

    private final IdempotentConsumer consumer;
    private final DispatchService dispatch;
    private final int trucks;

    public CarrierCommandListener(IdempotentConsumer consumer, DispatchService dispatch,
                                  @Value("${app.carrier.trucks:10}") int trucks) {
        this.consumer = consumer;
        this.dispatch = dispatch;
        this.trucks = trucks;
    }

    @KafkaListener(topics = Topics.CARRIER_COMMANDS)
    public void onCommand(ConsumerRecord<String, byte[]> record) {
        consumer.handle(record.value(), command -> {
            switch (command.getBodyCase()) {
                case REQUEST_PICKUP -> dispatch.requestPickup(command.getRequestPickup());
                case CONFIRM_LOADED -> dispatch.confirmLoaded(command.getConfirmLoaded().getOrderId());
                default -> {
                    // not a carrier command
                }
            }
        });
    }

    /** We only care where the warehouse is: trucks have to be in the same world as the packages. */
    @KafkaListener(topics = Topics.WAREHOUSE_EVENTS)
    public void onWarehouseEvent(ConsumerRecord<String, byte[]> record) {
        consumer.handle(record.value(), event -> {
            if (event.hasWorldAdopted()) {
                dispatch.adoptWorld(event.getWorldAdopted().getWorldId(), event.getWorldAdopted().getGeneration(), trucks);
            }
        });
    }
}
