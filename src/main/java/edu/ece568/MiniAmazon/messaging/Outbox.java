package edu.ece568.MiniAmazon.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * Transactional outbox: messages to other systems are rows written in the same
 * transaction as the business change, then delivered by a relay until they are
 * acknowledged. Plain SQL on purpose; the statements are the design.
 */
@Component
public class Outbox {

    private final JdbcClient jdbc;
    private final long retryBaseMs;

    public Outbox(JdbcClient jdbc, @Value("${app.outbox.retry-base-ms:1000}") long retryBaseMs) {
        this.jdbc = jdbc;
        this.retryBaseMs = retryBaseMs;
    }

    /**
     * Records a message and returns its id. The id is reserved first so the
     * payload can carry it (the world protocol wants the sequence number
     * inside the message). Joins the caller's transaction.
     */
    @Transactional
    public long enqueue(Destination destination, String type, Long orderId, LongFunction<byte[]> payloadForId) {
        long id = jdbc.sql("SELECT nextval('outbox_message_seq')").query(Long.class).single();
        jdbc.sql("""
                        INSERT INTO outbox_message (id, destination, type, order_id, payload)
                        VALUES (:id, :destination, :type, :orderId, :payload)""")
                .param("id", id)
                .param("destination", destination.name())
                .param("type", type)
                .param("orderId", orderId)
                .param("payload", payloadForId.apply(id))
                .update();
        return id;
    }

    /**
     * Hands out messages that are due, and in the same statement pushes their
     * next attempt into the future (1 s, doubling, capped at 16 s). The base
     * is set from measurement: the real simulator acknowledges a command only
     * when it has carried it out, 4 to 15 seconds later, so retrying after
     * 200 ms resent every command five times for nothing.
     *
     * Nothing is held while the caller does network I/O: the "lock" on a
     * message is just its next_attempt_at. If the send is lost, or the
     * process dies right after this call, the message simply becomes due
     * again. SKIP LOCKED lets several relays poll at once without waiting on,
     * or double-sending, each other's rows.
     */
    @Transactional
    public List<OutboxMessage> claimDue(Destination destination, int limit) {
        List<OutboxMessage> claimed = jdbc.sql("""
                        UPDATE outbox_message
                           SET attempts = attempts + 1,
                               next_attempt_at = now() + (:baseMs * power(2, least(attempts, 4))) * interval '1 millisecond'
                         WHERE id IN (SELECT id
                                        FROM outbox_message
                                       WHERE destination = :destination
                                         AND status = 'PENDING'
                                         AND next_attempt_at <= now()
                                       ORDER BY id
                                       LIMIT :limit
                                         FOR UPDATE SKIP LOCKED)
                        RETURNING id, type, order_id, payload, attempts""")
                .param("baseMs", retryBaseMs)
                .param("destination", destination.name())
                .param("limit", limit)
                .query((rs, n) -> new OutboxMessage(rs.getLong("id"), rs.getString("type"),
                        (Long) rs.getObject("order_id"), rs.getBytes("payload"), rs.getInt("attempts")))
                .list();
        return claimed.stream().sorted(Comparator.comparingLong(OutboxMessage::id)).toList();
    }

    /** The other side acknowledged it. True only for the first acknowledgement. */
    @Transactional
    public boolean markDone(long id) {
        return jdbc.sql("""
                        UPDATE outbox_message SET status = 'DONE', completed_at = now()
                         WHERE id = :id AND status = 'PENDING'""")
                .param("id", id).update() == 1;
    }

    /** The other side refused it; retrying the same message will not help. Kept for an operator to look at. */
    @Transactional
    public boolean markFailed(long id, String error) {
        return jdbc.sql("""
                        UPDATE outbox_message SET status = 'FAILED', last_error = :error, completed_at = now()
                         WHERE id = :id AND status <> 'FAILED'""")
                .param("id", id).param("error", error).update() == 1;
    }

    /** Operator decision: send a failed message again. */
    @Transactional
    public boolean retry(long id) {
        return jdbc.sql("""
                        UPDATE outbox_message
                           SET status = 'PENDING', attempts = 0, next_attempt_at = now(), completed_at = NULL
                         WHERE id = :id AND status = 'FAILED'""")
                .param("id", id).update() == 1;
    }

    /** Pending messages for a world that no longer exists can never be acknowledged. */
    @Transactional
    public int failAllPending(Destination destination, String reason) {
        return jdbc.sql("""
                        UPDATE outbox_message SET status = 'FAILED', last_error = :reason, completed_at = now()
                         WHERE destination = :destination AND status = 'PENDING'""")
                .param("destination", destination.name()).param("reason", reason).update();
    }

    @Transactional(readOnly = true)
    public Optional<OutboxMessage> find(long id) {
        return jdbc.sql("SELECT id, type, order_id, payload, attempts FROM outbox_message WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new OutboxMessage(rs.getLong("id"), rs.getString("type"),
                        (Long) rs.getObject("order_id"), rs.getBytes("payload"), rs.getInt("attempts")))
                .optional();
    }

    @Transactional(readOnly = true)
    public long count(Destination destination, String status) {
        return jdbc.sql("SELECT count(*) FROM outbox_message WHERE destination = :destination AND status = :status")
                .param("destination", destination.name()).param("status", status)
                .query(Long.class).single();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFailed(int limit) {
        return jdbc.sql("""
                        SELECT id, destination, type, order_id, attempts, last_error, created_at, completed_at
                          FROM outbox_message WHERE status = 'FAILED' ORDER BY id DESC LIMIT :limit""")
                .param("limit", limit).query().listOfRows();
    }
}
