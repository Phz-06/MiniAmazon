package edu.ece568.MiniAmazon.shared.worldlink;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** The world a service belongs to, kept in its database so a restart rejoins it. */
public class WorldSession {

    private final JdbcClient jdbc;

    public WorldSession(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @return null until the service has joined a world */
    @Transactional(readOnly = true)
    public Long worldId() {
        // The row always exists; the column is NULL until the first world.
        return jdbc.sql("SELECT world_id FROM world_session WHERE id = 1")
                .query((rs, n) -> Optional.ofNullable((Long) rs.getObject("world_id"))).single().orElse(null);
    }

    /**
     * How many worlds we have lived in; 0 before the first. Two worlds can
     * share an id (the simulator reuses ids after a restart) but never a generation.
     */
    @Transactional(readOnly = true)
    public long generation() {
        return jdbc.sql("SELECT generation FROM world_session WHERE id = 1").query(Long.class).single();
    }

    @Transactional
    public void save(long worldId, long generation) {
        jdbc.sql("UPDATE world_session SET world_id = :worldId, generation = :generation, updated_at = now() WHERE id = 1")
                .param("worldId", worldId).param("generation", generation).update();
    }
}
