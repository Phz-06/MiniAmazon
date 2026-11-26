package edu.ece568.MiniAmazon.shared.testing;

import com.google.protobuf.InvalidProtocolBufferException;
import edu.ece568.MiniAmazon.proto.Events.Envelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The test's own seat on the Kafka topics: it records everything published,
 * can publish as if it were another service, and can answer records as they
 * arrive (to play the part of a service that is not running).
 */
public class KafkaProbe implements Closeable {

    public record Seen(String topic, String key, byte[] raw, Envelope envelope) {
    }

    private final KafkaProducer<String, byte[]> producer;
    private final KafkaConsumer<String, byte[]> consumer;
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<KafkaProbe, Seen>> responders = new CopyOnWriteArrayList<>();
    private volatile boolean closed;
    private final Thread poller;

    public KafkaProbe(String bootstrapServers, String... topics) {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
        consumer.subscribe(List.of(topics));
        poller = new Thread(this::poll, "kafka-probe");
        poller.setDaemon(true);
        poller.start();
    }

    private void poll() {
        while (!closed) {
            for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(100))) {
                Envelope envelope = null;
                try {
                    envelope = Envelope.parseFrom(record.value());
                } catch (InvalidProtocolBufferException ignored) {
                    // kept as raw bytes only
                }
                Seen one = new Seen(record.topic(), record.key(), record.value(), envelope);
                seen.add(one);
                for (BiConsumer<KafkaProbe, Seen> responder : responders) {
                    responder.accept(this, one);
                }
            }
        }
        consumer.close();
    }

    /** Runs for every record the probe sees from now on. Register responders before producing anything. */
    public void respond(BiConsumer<KafkaProbe, Seen> responder) {
        responders.add(responder);
    }

    public static Envelope envelope(String id, Consumer<Envelope.Builder> body) {
        Envelope.Builder builder = Envelope.newBuilder().setId(id).setOccurredAtMs(System.currentTimeMillis());
        body.accept(builder);
        return builder.build();
    }

    public void send(String topic, String key, Envelope envelope) {
        sendRaw(topic, key, envelope.toByteArray());
    }

    /** The same record twice, as an at-least-once producer may deliver it. */
    public void sendTwice(String topic, String key, Envelope envelope) {
        send(topic, key, envelope);
        send(topic, key, envelope);
    }

    public void sendRaw(String topic, String key, byte[] value) {
        producer.send(new ProducerRecord<>(topic, key, value));
        producer.flush();
    }

    public List<Seen> on(String topic) {
        return seen.stream().filter(s -> s.topic().equals(topic)).toList();
    }

    /** Readable envelopes on a topic that match. */
    public List<Envelope> envelopes(String topic, Predicate<Envelope> matching) {
        List<Envelope> out = new ArrayList<>();
        for (Seen one : on(topic)) {
            if (one.envelope() != null && matching.test(one.envelope())) {
                out.add(one.envelope());
            }
        }
        return out;
    }

    /** How many different messages (by id) match: a relay publishing a row twice counts once. */
    public long distinct(String topic, Predicate<Envelope> matching) {
        return envelopes(topic, matching).stream().map(Envelope::getId).distinct().count();
    }

    public static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for " + what);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            poller.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        producer.close();
    }
}
