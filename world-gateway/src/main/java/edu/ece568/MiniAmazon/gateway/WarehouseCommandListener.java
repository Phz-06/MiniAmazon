package edu.ece568.MiniAmazon.gateway;

import edu.ece568.MiniAmazon.proto.Events.LoadPackage;
import edu.ece568.MiniAmazon.proto.Events.PackOrder;
import edu.ece568.MiniAmazon.proto.Events.PurchaseStock;
import edu.ece568.MiniAmazon.shared.messaging.IdempotentConsumer;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Accepts warehouse commands from Kafka and queues them for the world.
 *
 * "Seen this Kafka message" and "queued this world command" are one
 * transaction, so a redelivered Kafka record cannot become a second command
 * with a new sequence number, which the world would execute a second time.
 */
@Component
public class WarehouseCommandListener {

    private final IdempotentConsumer consumer;
    private final WorldCommandStore commands;

    public WarehouseCommandListener(IdempotentConsumer consumer, WorldCommandStore commands) {
        this.consumer = consumer;
        this.commands = commands;
    }

    @KafkaListener(topics = Topics.WAREHOUSE_COMMANDS)
    public void onCommand(ConsumerRecord<String, byte[]> record) {
        consumer.handle(record.value(), command -> {
            switch (command.getBodyCase()) {
                case PURCHASE_STOCK -> {
                    PurchaseStock purchase = command.getPurchaseStock();
                    commands.enqueue(AmazonProtocol.PURCHASE, null,
                            seq -> AmazonProtocol.purchase(seq, purchase.getWarehouseId(), purchase.getItemsList()));
                }
                case PACK_ORDER -> {
                    PackOrder pack = command.getPackOrder();
                    commands.enqueue(AmazonProtocol.PACK, pack.getOrderId(),
                            seq -> AmazonProtocol.pack(seq, pack.getWarehouseId(), pack.getOrderId(), pack.getItemsList()));
                }
                case LOAD_PACKAGE -> {
                    LoadPackage load = command.getLoadPackage();
                    commands.enqueue(AmazonProtocol.LOAD, load.getOrderId(),
                            seq -> AmazonProtocol.load(seq, load.getWarehouseId(), load.getTruckId(), load.getOrderId()));
                }
                default -> {
                    // not a warehouse command
                }
            }
        });
    }
}
