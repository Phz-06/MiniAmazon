package edu.ece568.MiniAmazon.world;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The world we belong to, kept in the database so a restart rejoins it instead of starting over. */
@Component
public class WorldSession {

    public record State(Long worldId, boolean autoConnect) {
    }

    private final JdbcClient jdbc;

    public WorldSession(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public State get() {
        return jdbc.sql("SELECT world_id, auto_connect FROM world_session WHERE id = 1")
                .query((rs, n) -> new State((Long) rs.getObject("world_id"), rs.getBoolean("auto_connect")))
                .single();
    }

    @Transactional
    public void save(Long worldId, boolean autoConnect) {
        jdbc.sql("UPDATE world_session SET world_id = :worldId, auto_connect = :autoConnect, updated_at = now() WHERE id = 1")
                .param("worldId", worldId).param("autoConnect", autoConnect).update();
    }

    @Transactional
    public void setAutoConnect(boolean autoConnect) {
        jdbc.sql("UPDATE world_session SET auto_connect = :autoConnect, updated_at = now() WHERE id = 1")
                .param("autoConnect", autoConnect).update();
    }
}
