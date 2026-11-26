package edu.ece568.MiniAmazon.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Remembers which world messages have already been applied. */
@Component
public class WorldInbox {

    private final JdbcClient jdbc;

    public WorldInbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * True the first time a message is seen, false for every redelivery.
     *
     * Must run in the same transaction as the message's effect: if the effect
     * fails, this row rolls back with it and the redelivery gets another try;
     * if it commits, the effect can never be applied twice. Two deliveries
     * racing each other are settled by the primary key.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(long worldId, long seqnum, String kind) {
        return jdbc.sql("""
                        INSERT INTO world_inbox (world_id, seqnum, kind) VALUES (:worldId, :seqnum, :kind)
                        ON CONFLICT DO NOTHING""")
                .param("worldId", worldId).param("seqnum", seqnum).param("kind", kind)
                .update() == 1;
    }
}
