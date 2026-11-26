package edu.ece568.MiniAmazon.shared.worldlink;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * Commands waiting to be acknowledged by the world.
 *
 * The world may ignore anything it receives, so a command is not "sent", it
 * is "pending until acked". The row id is the protocol's sequence number: it
 * is unique, survives restarts, and a resend carries the same number, which
 * is what lets the world execute it once.
 */
public class WorldCommandStore {

    private final JdbcClient jdbc;
    private final long retryBaseMs;

    public WorldCommandStore(JdbcClient jdbc, long retryBaseMs) {
        this.jdbc = jdbc;
        this.retryBaseMs = retryBaseMs;
    }

    /**
     * @param refId          what the command is about (an order id), for diagnostics
     * @param payloadForSeq  builds the protocol message once its sequence number is known
     */
    @Transactional
    public long enqueue(String type, Long refId, LongFunction<byte[]> payloadForSeq) {
        long seq = jdbc.sql("SELECT nextval('world_command_seq')").query(Long.class).single();
        jdbc.sql("INSERT INTO world_command (id, type, ref_id, payload) VALUES (:id, :type, :refId, :payload)")
                .param("id", seq).param("type", type).param("refId", refId)
                .param("payload", payloadForSeq.apply(seq))
                .update();
        return seq;
    }

    /**
     * Commands that are due, with their next attempt already pushed out
     * (1 s, doubling, capped at 16 s). The base comes from measurement: the
     * real simulator acknowledges a command only when it has carried it out,
     * 4 to 15 seconds later, so anything faster just repeats itself.
     */
    @Transactional
    public List<StoredCommand> claimDue(int limit) {
        return jdbc.sql("""
                        UPDATE world_command
                           SET attempts = attempts + 1,
                               next_attempt_at = now() + (:baseMs * power(2, least(attempts, 4))) * interval '1 millisecond'
                         WHERE id IN (SELECT id FROM world_command
                                       WHERE status = 'PENDING' AND next_attempt_at <= now()
                                       ORDER BY id
                                       LIMIT :limit
                                         FOR UPDATE SKIP LOCKED)
                        RETURNING id, type, ref_id, payload, attempts""")
                .param("baseMs", retryBaseMs).param("limit", limit)
                .query((rs, n) -> new StoredCommand(rs.getLong("id"), rs.getString("type"),
                        (Long) rs.getObject("ref_id"), rs.getBytes("payload"), rs.getInt("attempts")))
                .list().stream().sorted(Comparator.comparingLong(StoredCommand::id)).toList();
    }

    /** The world acknowledged it. True only for the first acknowledgement. */
    @Transactional
    public boolean markDone(long seq) {
        return jdbc.sql("UPDATE world_command SET status = 'DONE', completed_at = now() WHERE id = :id AND status = 'PENDING'")
                .param("id", seq).update() == 1;
    }

    /** The world refused it; sending the same thing again would be refused again. */
    @Transactional
    public boolean markFailed(long seq, String error) {
        return jdbc.sql("""
                        UPDATE world_command SET status = 'FAILED', last_error = :error, completed_at = now()
                         WHERE id = :id AND status <> 'FAILED'""")
                .param("id", seq).param("error", error).update() == 1;
    }

    /** Commands for a world we have left can never be acknowledged. */
    @Transactional
    public int failAllPending(String reason) {
        return jdbc.sql("""
                        UPDATE world_command SET status = 'FAILED', last_error = :reason, completed_at = now()
                         WHERE status = 'PENDING'""")
                .param("reason", reason).update();
    }

    @Transactional(readOnly = true)
    public Optional<StoredCommand> find(long seq) {
        return jdbc.sql("SELECT id, type, ref_id, payload, attempts FROM world_command WHERE id = :id")
                .param("id", seq)
                .query((rs, n) -> new StoredCommand(rs.getLong("id"), rs.getString("type"),
                        (Long) rs.getObject("ref_id"), rs.getBytes("payload"), rs.getInt("attempts")))
                .optional();
    }

    @Transactional(readOnly = true)
    public long count(String status) {
        return jdbc.sql("SELECT count(*) FROM world_command WHERE status = :status")
                .param("status", status).query(Long.class).single();
    }
}
