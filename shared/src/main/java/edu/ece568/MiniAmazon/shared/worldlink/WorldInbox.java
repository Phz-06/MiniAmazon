package edu.ece568.MiniAmazon.shared.worldlink;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Which messages from the world have already been applied. */
public class WorldInbox {

    private final JdbcClient jdbc;

    public WorldInbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * True the first time a message is seen, false for every redelivery.
     * Must run in the same transaction as the message's effect: a failed
     * effect rolls this row back and the redelivery gets another try; a
     * committed one can never be applied twice.
     */
    /** A new world numbers its messages from the start again, possibly under a reused id. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void forgetEverything() {
        jdbc.sql("DELETE FROM world_inbox").update();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(long worldId, long seqnum, String kind) {
        return jdbc.sql("""
                        INSERT INTO world_inbox (world_id, seqnum, kind) VALUES (:worldId, :seqnum, :kind)
                        ON CONFLICT DO NOTHING""")
                .param("worldId", worldId).param("seqnum", seqnum).param("kind", kind)
                .update() == 1;
    }
}
