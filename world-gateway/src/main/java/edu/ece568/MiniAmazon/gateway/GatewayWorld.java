package edu.ece568.MiniAmazon.gateway;

import edu.ece568.MiniAmazon.proto.Events.WorldAdopted;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import edu.ece568.MiniAmazon.shared.messaging.EventOutbox;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import edu.ece568.MiniAmazon.shared.worldlink.WorldInbox;
import edu.ece568.MiniAmazon.shared.worldlink.WorldLink;
import edu.ece568.MiniAmazon.shared.worldlink.WorldRejectedException;
import edu.ece568.MiniAmazon.shared.worldlink.WorldSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

/**
 * Keeps the gateway in its world. No operator step: with no world on record it
 * creates one and announces it; otherwise it rejoins the one it belongs to,
 * after a restart or a dropped socket alike.
 *
 * A refused rejoin is ambiguous: the simulator gives the same answer for "the
 * previous connection is not gone yet" (routine right after a crash, clears
 * in seconds) and for "no such world". The second really happens, because the
 * simulator drops all its tables when it starts: restart it and every world
 * is gone for good. So a refusal is retried, and only when it has been
 * refused continuously for app.world.recreate-after-refused-ms do we conclude
 * the world is lost, create a new one and announce it. That window is the
 * trade-off: too short and a slow simulator costs us a live world, too long
 * and a dead one keeps the shop closed.
 */
@Component
public class GatewayWorld {
    private static final Logger log = LoggerFactory.getLogger(GatewayWorld.class);

    private final WorldLink<ACommands, AResponses> link;
    private final WorldSession session;
    private final WorldCommandStore commands;
    private final EventOutbox outbox;
    private final WorldInbox inbox;
    private final TransactionTemplate tx;
    private final int simSpeed;
    private final long recreateAfterRefusedMs;

    private volatile boolean ready;
    private long refusedSince;
    private long nextAttemptAt;
    private long retryDelayMs = 1_000;

    public GatewayWorld(WorldLink<ACommands, AResponses> link, WorldSession session, WorldCommandStore commands,
                        EventOutbox outbox, WorldInbox inbox, TransactionTemplate tx, @Value("${app.world.sim-speed:0}") int simSpeed,
                        @Value("${app.world.recreate-after-refused-ms:30000}") long recreateAfterRefusedMs) {
        this.link = link;
        this.session = session;
        this.commands = commands;
        this.outbox = outbox;
        this.inbox = inbox;
        this.tx = tx;
        this.simSpeed = simSpeed;
        this.recreateAfterRefusedMs = recreateAfterRefusedMs;
    }

    @Scheduled(fixedDelayString = "${app.world.pump-interval-ms:100}")
    public void pump() {
        if (ready) {
            link.pump();
        }
    }

    @Scheduled(fixedDelay = 500)
    public void ensureConnected() {
        if (link.isConnected() || System.currentTimeMillis() < nextAttemptAt) {
            return;
        }
        ready = false;
        Long known = session.worldId();
        try {
            if (known == null || worldIsLost(known)) {
                adopt(link.connect(null, true));
            } else {
                link.connect(known, false);
            }
            if (simSpeed > 0) {
                link.setSimSpeed(simSpeed);
            }
            ready = true;
            refusedSince = 0;
            retryDelayMs = 1_000;
        } catch (WorldRejectedException e) {
            if (refusedSince == 0) {
                refusedSince = System.currentTimeMillis();
            }
            log.warn("World {} refused us ({}); retrying", known, e.reply().lines().findFirst().orElse(""));
            // Keep asking at a steady pace: the verdict in worldIsLost depends on elapsed time, not on attempts.
            nextAttemptAt = System.currentTimeMillis() + 1_000;
        } catch (IOException | RuntimeException e) {
            link.close();
            refusedSince = 0; // unreachable is not the same as refused
            log.warn("Could not {} ({}); next try in {} ms",
                    known == null ? "create a world" : "rejoin world " + known, e.getMessage(), retryDelayMs);
            nextAttemptAt = System.currentTimeMillis() + retryDelayMs;
            retryDelayMs = Math.min(retryDelayMs * 2, 30_000);
        }
    }

    private boolean worldIsLost(long known) {
        boolean lost = recreateAfterRefusedMs > 0 && refusedSince != 0
                && System.currentTimeMillis() - refusedSince >= recreateAfterRefusedMs;
        if (lost) {
            log.error("World {} has refused us for {} ms; treating it as lost and creating a new one", known, recreateAfterRefusedMs);
        }
        return lost;
    }

    /**
     * One transaction: we record the world under a new generation, forget the
     * previous world's commands and message numbers, and tell the other services. They react by buying stock again
     * (order-service) and registering trucks (carrier-service).
     */
    private void adopt(long worldId) {
        tx.executeWithoutResult(status -> {
            long generation = session.generation() + 1;
            session.save(worldId, generation);
            inbox.forgetEverything();
            int stale = commands.failAllPending("Superseded: now in world " + worldId);
            if (stale > 0) {
                log.warn("{} commands for the previous world will never be acknowledged; marked FAILED", stale);
            }
            outbox.publish(Topics.WAREHOUSE_EVENTS, "world",
                    e -> e.setWorldAdopted(WorldAdopted.newBuilder().setWorldId(worldId).setGeneration(generation)));
        });
        log.info("Created and adopted world {}", worldId);
    }
}
