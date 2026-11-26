package edu.ece568.MiniAmazon.shared.messaging;

import edu.ece568.MiniAmazon.proto.Events.Envelope;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Transactional outbox for Kafka.
 *
 * A service cannot commit to its database and publish to Kafka atomically:
 * whichever it does second can fail after the first succeeded. So publishing
 * is split in two. The message is inserted into event_outbox in the same
 * transaction as the state change it describes; KafkaOutboxRelay then sends
 * the row and marks it done once the broker has it. A crash in between means
 * the row is sent again, never that it is lost. Consumers cope with the
 * repeat (see IdempotentConsumer).
 */
public class EventOutbox {

    private final JdbcClient jdbc;
    private final String source;
    private final long retryBaseMs;

    /** @param source short name of the publishing service; becomes part of every message id */
    public EventOutbox(JdbcClient jdbc, String source, long retryBaseMs) {
        this.jdbc = jdbc;
        this.source = source;
        this.retryBaseMs = retryBaseMs;
    }

    /**
     * Records a message for a topic. Joins the caller's transaction.
     *
     * @param key  records with the same key keep their order; use the order id
     * @param body fills in exactly one of the envelope's bodies
     */
    @Transactional
    public long publish(String topic, String key, Consumer<Envelope.Builder> body) {
        long id = jdbc.sql("SELECT nextval('event_outbox_seq')").query(Long.class).single();
        Envelope.Builder envelope = Envelope.newBuilder()
                .setId(source + "-" + id)
                .setOccurredAtMs(System.currentTimeMillis());
        body.accept(envelope);
        jdbc.sql("INSERT INTO event_outbox (id, topic, msg_key, payload) VALUES (:id, :topic, :key, :payload)")
                .param("id", id).param("topic", topic).param("key", key)
                .param("payload", envelope.build().toByteArray())
                .update();
        return id;
    }

    /**
     * Hands out rows that are due and, in the same statement, pushes their
     * next attempt into the future. Nothing stays locked while the relay
     * talks to Kafka; a row whose send is lost just comes due again.
     * SKIP LOCKED lets several instances of a service relay at once.
     */
    @Transactional
    public List<OutboxRecord> claimDue(int limit) {
        return jdbc.sql("""
                        UPDATE event_outbox
                           SET attempts = attempts + 1,
                               next_attempt_at = now() + (:baseMs * power(2, least(attempts, 5))) * interval '1 millisecond'
                         WHERE id IN (SELECT id FROM event_outbox
                                       WHERE status = 'PENDING' AND next_attempt_at <= now()
                                       ORDER BY id
                                       LIMIT :limit
                                         FOR UPDATE SKIP LOCKED)
                        RETURNING id, topic, msg_key, payload, attempts""")
                .param("baseMs", retryBaseMs).param("limit", limit)
                .query((rs, n) -> new OutboxRecord(rs.getLong("id"), rs.getString("topic"),
                        rs.getString("msg_key"), rs.getBytes("payload"), rs.getInt("attempts")))
                .list().stream().sorted(Comparator.comparingLong(OutboxRecord::id)).toList();
    }

    @Transactional
    public boolean markDone(long id) {
        return jdbc.sql("UPDATE event_outbox SET status = 'DONE', completed_at = now() WHERE id = :id AND status = 'PENDING'")
                .param("id", id).update() == 1;
    }

    @Transactional(readOnly = true)
    public long pending() {
        return jdbc.sql("SELECT count(*) FROM event_outbox WHERE status = 'PENDING'").query(Long.class).single();
    }
}
