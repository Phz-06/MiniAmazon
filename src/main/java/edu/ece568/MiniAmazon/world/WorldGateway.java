package edu.ece568.MiniAmazon.world;

import com.google.protobuf.InvalidProtocolBufferException;
import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.messaging.OutboxMessage;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AInitWarehouse;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Owns the single connection to the world and makes an unreliable channel
 * behave like a reliable one.
 *
 * The world may drop any message we send, and resends its own messages until
 * we acknowledge them. So:
 *
 *   outbound  every command is an outbox row; pump() sends what is due and
 *             keeps resending, with backoff, until the world's ack marks the
 *             row DONE. A resend carries the same sequence number.
 *   inbound   every message is applied through the inbox, at most once, and
 *             acknowledged only after its effect is committed.
 *
 * Nothing needed for recovery is kept in this object. After a crash or a
 * dropped socket, the next connection (same world id, read from the database)
 * picks up exactly where the tables say we are.
 */
@Slf4j
@Component
public class WorldGateway {

    /** The one warehouse this application runs. */
    public static final int WAREHOUSE_ID = 1;
    private static final AInitWarehouse WAREHOUSE =
            AInitWarehouse.newBuilder().setId(WAREHOUSE_ID).setX(10).setY(20).build();

    private final Outbox outbox;
    private final WorldSession session;
    private final WorldBooks books;
    private final WorldResponseHandler handler;
    private final String host;
    private final int port;
    private final int connectTimeoutMs;

    private final Object lifecycle = new Object();
    private volatile WorldConnection connection;
    private long nextReconnectAt;
    private long reconnectDelayMs = 1_000;

    public WorldGateway(Outbox outbox, WorldSession session, WorldBooks books, WorldResponseHandler handler,
                        @Value("${world.host:localhost}") String host,
                        @Value("${world.port:23456}") int port,
                        @Value("${app.world.connect-timeout-ms:5000}") int connectTimeoutMs) {
        this.outbox = outbox;
        this.session = session;
        this.books = books;
        this.handler = handler;
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public boolean isConnected() {
        WorldConnection c = connection;
        return c != null && c.isOpen();
    }

    public Long worldId() {
        WorldConnection c = connection;
        return c != null && c.isOpen() ? Long.valueOf(c.worldId()) : null;
    }

    /**
     * Connects and remembers the decision, so the gateway reconnects by
     * itself from now on.
     *
     * @param worldId a specific world to join; null rejoins the world we
     *                already belong to, or creates one if there is none
     * @param fresh   create a new world even if we already belong to one
     */
    public long connect(Long worldId, boolean fresh) throws IOException {
        synchronized (lifecycle) {
            Long known = session.get().worldId();
            Long target = fresh ? null : (worldId != null ? worldId : known);
            return open(target, known);
        }
    }

    private long open(Long target, Long known) throws IOException {
        drop();
        WorldConnection c = WorldConnection.open(host, port, target,
                target == null ? List.of(WAREHOUSE) : List.of(), connectTimeoutMs);
        try {
            if (Objects.equals(c.worldId(), known)) {
                session.setAutoConnect(true);
            } else {
                // A world we have no history with: it has none of our stock.
                books.adopt(c.worldId());
            }
        } catch (RuntimeException e) {
            c.close();
            throw e;
        }
        connection = c;
        Thread reader = new Thread(() -> readLoop(c), "world-reader");
        reader.setDaemon(true);
        reader.start();
        log.info("Connected to world {}", c.worldId());
        return c.worldId();
    }

    /** Stops talking to the world and stops trying to. */
    public void disconnect() {
        synchronized (lifecycle) {
            session.setAutoConnect(false);
            WorldConnection c = connection;
            if (c != null) {
                try {
                    c.send(ACommands.newBuilder().setDisconnect(true).build());
                } catch (IOException ignored) {
                    // we are leaving anyway
                }
            }
            drop();
        }
    }

    public void setSimulationSpeed(int speed) throws IOException {
        WorldConnection c = connection;
        if (c == null) {
            throw new IOException("Not connected to the world");
        }
        c.send(ACommands.newBuilder().setSimspeed(speed).build());
    }

    /**
     * The relay. Sends every command that is due, as one batch.
     *
     * A command stays PENDING after it is sent; only the world's ack finishes
     * it. If the world dropped it, or we lose the connection, it comes due
     * again after its backoff and goes out in a later batch.
     */
    @Scheduled(fixedDelayString = "${app.world.pump-interval-ms:100}")
    public void pump() {
        WorldConnection c = connection;
        if (c == null) {
            return;
        }
        List<OutboxMessage> due = outbox.claimDue(Destination.WORLD, 200);
        if (due.isEmpty()) {
            return;
        }
        ACommands.Builder batch = ACommands.newBuilder();
        for (OutboxMessage message : due) {
            try {
                WorldCommands.addTo(batch, message);
            } catch (InvalidProtocolBufferException e) {
                outbox.markFailed(message.id(), "Unreadable payload: " + e.getMessage());
            }
        }
        try {
            c.send(batch.build());
        } catch (IOException e) {
            log.warn("Lost the world connection while sending: {}", e.getMessage());
            c.close();
        }
    }

    /** Reconnects to our world whenever we should be connected and are not. */
    @Scheduled(fixedDelay = 500)
    public void ensureConnected() {
        if (connection != null || System.currentTimeMillis() < nextReconnectAt) {
            return;
        }
        synchronized (lifecycle) {
            WorldSession.State state = session.get();
            if (connection != null || !state.autoConnect() || state.worldId() == null) {
                return;
            }
            try {
                open(state.worldId(), state.worldId());
                reconnectDelayMs = 1_000;
            } catch (IOException e) {
                log.warn("Could not rejoin world {} ({}); next try in {} ms",
                        state.worldId(), e.getMessage(), reconnectDelayMs);
                nextReconnectAt = System.currentTimeMillis() + reconnectDelayMs;
                reconnectDelayMs = Math.min(reconnectDelayMs * 2, 30_000);
            }
        }
    }

    private void readLoop(WorldConnection c) {
        try {
            while (true) {
                AResponses responses = c.read();
                List<Long> applied = handler.handle(c.worldId(), responses);
                if (!applied.isEmpty()) {
                    c.send(ACommands.newBuilder().addAllAcks(applied).build());
                }
            }
        } catch (IOException e) {
            if (c.isOpen()) {
                log.warn("World connection lost: {}", e.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("Unexpected failure in the world reader; dropping the connection", e);
        } finally {
            c.close();
            if (connection == c) {
                connection = null;
            }
        }
    }

    private void drop() {
        WorldConnection c = connection;
        connection = null;
        if (c != null) {
            c.close();
        }
    }

    @PreDestroy
    void shutdown() {
        // Keep auto_connect as it is: after a restart we want to come back.
        drop();
    }
}
