package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.WorldUps.UCommands;
import edu.ece568.MiniAmazon.proto.WorldUps.UResponses;
import edu.ece568.MiniAmazon.shared.worldlink.WorldLink;
import edu.ece568.MiniAmazon.shared.worldlink.WorldRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * Keeps the carrier in the same world as the warehouse. It never creates a
 * world; it follows the one the gateway announced, and registers its trucks
 * there exactly once.
 */
@Component
public class CarrierWorld {
    private static final Logger log = LoggerFactory.getLogger(CarrierWorld.class);

    private record Session(Long worldId, boolean trucksRegistered) {
    }

    private final WorldLink<UCommands, UResponses> link;
    private final DispatchService dispatch;
    private final JdbcClient jdbc;

    private long nextAttemptAt;
    private long retryDelayMs = 1_000;

    public CarrierWorld(WorldLink<UCommands, UResponses> link, DispatchService dispatch, JdbcClient jdbc) {
        this.link = link;
        this.dispatch = dispatch;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${app.world.pump-interval-ms:100}")
    public void pump() {
        link.pump();
    }

    /** Packages can be waiting for a truck with nothing happening to trigger a dispatch. */
    @Scheduled(fixedDelayString = "${app.carrier.dispatch-interval-ms:1000}")
    public void dispatchWaiting() {
        if (link.isConnected()) {
            dispatch.dispatch();
        }
    }

    @Scheduled(fixedDelay = 500)
    public void ensureConnected() {
        Session session = jdbc.sql("SELECT world_id, trucks_registered FROM world_session WHERE id = 1")
                .query((rs, n) -> new Session((Long) rs.getObject("world_id"), rs.getBoolean("trucks_registered")))
                .single();
        if (session.worldId() == null) {
            return; // the gateway has not announced a world yet
        }
        if (link.isConnected() && (!Objects.equals(link.worldId(), session.worldId()) || !session.trucksRegistered())) {
            // The warehouse moved (possibly to a new world with the same id):
            // we are connected to a world that does not have our trucks.
            link.close();
        }
        if (link.isConnected() || System.currentTimeMillis() < nextAttemptAt) {
            return;
        }
        try {
            link.connect(session.worldId(), !session.trucksRegistered());
            trucksRegistered();
            retryDelayMs = 1_000;
        } catch (WorldRejectedException e) {
            // A crash between the world accepting our trucks and us recording it
            // lands here on the next start. The trucks are there; just join.
            if (!session.trucksRegistered() && e.reply().contains("already exists")) {
                trucksRegistered();
            } else {
                backOff(session.worldId(), e);
            }
        } catch (IOException | RuntimeException e) {
            link.close();
            backOff(session.worldId(), e);
        }
    }

    private void trucksRegistered() {
        jdbc.sql("UPDATE world_session SET trucks_registered = true, updated_at = now() WHERE id = 1").update();
    }

    private void backOff(long worldId, Exception e) {
        log.warn("Could not join world {} ({}); next try in {} ms", worldId, e.getMessage(), retryDelayMs);
        nextAttemptAt = System.currentTimeMillis() + retryDelayMs;
        retryDelayMs = Math.min(retryDelayMs * 2, 30_000);
    }
}
