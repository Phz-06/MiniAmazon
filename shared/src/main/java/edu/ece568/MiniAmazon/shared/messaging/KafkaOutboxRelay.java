package edu.ece568.MiniAmazon.shared.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Moves outbox rows to Kafka. A row is done only after the broker has
 * acknowledged it (acks=all), so the worst case is a duplicate, not a loss.
 *
 * The whole batch is handed to the producer first and the acknowledgements
 * are collected afterwards: one network round trip per batch instead of one
 * per row. The producer is idempotent, so rows of one batch reach a partition
 * in the order they were sent.
 */
public class KafkaOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxRelay.class);

    private final EventOutbox outbox;
    private final KafkaTemplate<String, byte[]> kafka;

    public KafkaOutboxRelay(EventOutbox outbox, KafkaTemplate<String, byte[]> kafka) {
        this.outbox = outbox;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${app.outbox.pump-interval-ms:100}")
    public void pump() {
        List<OutboxRecord> due = outbox.claimDue(500);
        if (due.isEmpty()) {
            return;
        }
        List<CompletableFuture<SendResult<String, byte[]>>> sends = new ArrayList<>(due.size());
        for (OutboxRecord record : due) {
            sends.add(kafka.send(record.topic(), record.key(), record.payload()));
        }
        for (int i = 0; i < due.size(); i++) {
            try {
                sends.get(i).get(10, TimeUnit.SECONDS);
                outbox.markDone(due.get(i).id());
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Kafka did not take outbox row {} (attempt {}); it stays pending: {}",
                        due.get(i).id(), due.get(i).attempts(), e.getMessage());
            }
        }
    }
}
