package edu.ece568.MiniAmazon.shared.messaging;

import com.google.protobuf.InvalidProtocolBufferException;
import edu.ece568.MiniAmazon.proto.Events.Envelope;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

/**
 * Turns Kafka's at-least-once delivery into exactly-once effects.
 *
 * Kafka redelivers whenever a consumer dies or is rebalanced after doing the
 * work but before its offset is committed, and the outbox relay on the other
 * side can publish a row twice. So the handler and a "seen this id" row are
 * written in one database transaction. A repeat finds the row and does
 * nothing. If the handler throws, both roll back and the listener container
 * retries the record, then parks it on the dead-letter topic.
 */
public class IdempotentConsumer {

    private final ProcessedMessages processed;
    private final TransactionTemplate tx;

    public IdempotentConsumer(ProcessedMessages processed, TransactionTemplate tx) {
        this.processed = processed;
        this.tx = tx;
    }

    public void handle(byte[] record, Consumer<Envelope> handler) {
        Envelope envelope;
        try {
            envelope = Envelope.parseFrom(record);
        } catch (InvalidProtocolBufferException e) {
            throw new UnreadableMessageException(e);
        }
        tx.executeWithoutResult(status -> {
            if (processed.firstTime(envelope.getId())) {
                handler.accept(envelope);
            }
        });
    }
}
