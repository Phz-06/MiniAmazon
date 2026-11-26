package edu.ece568.MiniAmazon.shared.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The messaging every service uses: outbox and relay on the way out,
 * idempotent consumer with retry and dead-lettering on the way in.
 * Each service imports this and owns the tables (event_outbox,
 * processed_message) in its own database.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SharedMessagingConfig {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SharedMessagingConfig.class);

    @Bean
    EventOutbox eventOutbox(JdbcClient jdbc,
                            @Value("${spring.application.name}") String service,
                            @Value("${app.outbox.retry-base-ms:1000}") long retryBaseMs) {
        return new EventOutbox(jdbc, service, retryBaseMs);
    }

    @Bean
    KafkaOutboxRelay kafkaOutboxRelay(EventOutbox outbox, KafkaTemplate<String, byte[]> kafka) {
        return new KafkaOutboxRelay(outbox, kafka);
    }

    @Bean
    ProcessedMessages processedMessages(JdbcClient jdbc) {
        return new ProcessedMessages(jdbc);
    }

    @Bean
    IdempotentConsumer idempotentConsumer(ProcessedMessages processed, TransactionTemplate tx) {
        return new IdempotentConsumer(processed, tx);
    }

    /**
     * A failing record is retried in place, five times with growing pauses,
     * and then published to "<topic>.DLT" so the partition can move on.
     *
     * Retrying in place blocks the partition, on purpose. The alternative,
     * moving the record to a retry topic, would let later records for the
     * same order overtake it, and the steps of an order must stay in order.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, byte[]> kafka,
                                          @Value("${app.kafka.retry-initial-ms:500}") long initialMs) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(initialMs);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);
        // Same partition number on "<topic>.DLT": dead letters of one order stay together and in order.
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafka,
                (record, failure) -> new TopicPartition(record.topic() + Topics.DEAD_LETTER_SUFFIX, record.partition()));
        // Giving up on a record must be loud: say which one and why, then park it.
        DefaultErrorHandler handler = new DefaultErrorHandler((record, failure) -> {
            log.error("Giving up on {}-{}@{} after retries; moving it to {}{}",
                    record.topic(), record.partition(), record.offset(), record.topic(), Topics.DEAD_LETTER_SUFFIX, failure);
            deadLetters.accept(record, failure);
        }, backOff);
        handler.addNotRetryableExceptions(UnreadableMessageException.class);
        return handler;
    }

    /** Topics are declared in code, so a fresh broker ends up with the same layout everywhere. */
    @Bean
    KafkaAdmin.NewTopics miniAmazonTopics(@Value("${app.kafka.partitions:3}") int partitions) {
        List<NewTopic> topics = new ArrayList<>();
        for (String name : List.of(Topics.WAREHOUSE_COMMANDS, Topics.WAREHOUSE_EVENTS,
                Topics.CARRIER_COMMANDS, Topics.CARRIER_EVENTS)) {
            topics.add(TopicBuilder.name(name).partitions(partitions).replicas(1).build());
            topics.add(TopicBuilder.name(name + Topics.DEAD_LETTER_SUFFIX).partitions(partitions).replicas(1).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
}
