package edu.ece568.MiniAmazon.shared.worldlink;

import com.google.protobuf.MessageLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * A connection to the world that behaves reliably although the world does not.
 *
 *   outbound  commands live in WorldCommandStore; pump() sends what is due
 *             and keeps resending, with backoff, until the world's ack marks
 *             the row DONE.
 *   inbound   the listener applies each message at most once (through
 *             WorldInbox) and returns the sequence numbers it has committed;
 *             only those are acknowledged.
 *
 * Nothing needed for recovery is kept in this object: after a crash or a
 * dropped socket, the next connection continues from what the tables say.
 *
 * Framing is a varint length followed by the message. The streams are created
 * once per connection and reused; parseDelimitedFrom then reads exactly one
 * message. (The original code built a new CodedInputStream per message, which
 * reads ahead into a private buffer and so swallowed the second of two
 * messages that arrived together.)
 */
public final class WorldLink<C extends MessageLite, R extends MessageLite> {
    private static final Logger log = LoggerFactory.getLogger(WorldLink.class);

    /** Applies the world's messages. Returns the sequence numbers that are safely committed. */
    public interface Listener<R> {
        List<Long> onResponses(long worldId, R responses);
    }

    private record Connection(Socket socket, InputStream in, OutputStream out, long worldId) {
        boolean isOpen() {
            return !socket.isClosed();
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing is best effort
            }
        }
    }

    private final String name;
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final WorldProtocol<C, R> protocol;
    private final WorldCommandStore commands;
    private final Listener<R> listener;

    private final Object lifecycle = new Object();
    private volatile Connection connection;

    public WorldLink(String name, String host, int port, int connectTimeoutMs, WorldProtocol<C, R> protocol,
                     WorldCommandStore commands, Listener<R> listener) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.protocol = protocol;
        this.commands = commands;
        this.listener = listener;
    }

    public boolean isConnected() {
        Connection c = connection;
        return c != null && c.isOpen();
    }

    public Long worldId() {
        Connection c = connection;
        return c != null && c.isOpen() ? Long.valueOf(c.worldId()) : null;
    }

    /**
     * Replaces any existing connection.
     *
     * @return the id of the world we are now in
     * @throws WorldRejectedException if the world answered but said no
     */
    public long connect(Long worldId, boolean initialise) throws IOException {
        synchronized (lifecycle) {
            close();
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
                socket.setSoTimeout(connectTimeoutMs);
                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);
                InputStream in = new BufferedInputStream(socket.getInputStream());
                OutputStream out = new BufferedOutputStream(socket.getOutputStream());

                protocol.hello(worldId, initialise).writeDelimitedTo(out);
                out.flush();
                WorldProtocol.Handshake reply = protocol.readHandshake(in);
                if (reply == null) {
                    throw new EOFException("World closed the connection during the handshake");
                }
                if (!"connected!".equals(reply.result())) {
                    throw new WorldRejectedException(reply.result());
                }
                // From here on silence is normal: the world speaks only when something happens.
                socket.setSoTimeout(0);

                Connection c = new Connection(socket, in, out, reply.worldId());
                connection = c;
                Thread reader = new Thread(() -> readLoop(c), name + "-world-reader");
                reader.setDaemon(true);
                reader.start();
                log.info("{}: connected to world {}", name, c.worldId());
                return c.worldId();
            } catch (IOException | RuntimeException e) {
                socket.close();
                throw e;
            }
        }
    }

    /** Sends every command that is due, as one batch. Call it on a schedule. */
    public void pump() {
        Connection c = connection;
        if (c == null) {
            return;
        }
        List<StoredCommand> due = commands.claimDue(200);
        if (due.isEmpty()) {
            return;
        }
        C batch = protocol.batch(due, (command, why) -> commands.markFailed(command.id(), "Unreadable payload: " + why));
        try {
            send(c, batch);
        } catch (IOException e) {
            log.warn("{}: lost the world connection while sending: {}", name, e.getMessage());
            c.close();
        }
    }

    public void setSimSpeed(int speed) throws IOException {
        Connection c = connection;
        if (c == null) {
            throw new IOException("Not connected to the world");
        }
        send(c, protocol.simSpeed(speed));
    }

    public void close() {
        Connection c = connection;
        connection = null;
        if (c != null) {
            c.close();
        }
    }

    private void readLoop(Connection c) {
        try {
            while (true) {
                R responses = protocol.readResponses(c.in());
                if (responses == null) {
                    throw new EOFException("World closed the connection");
                }
                for (long acked : protocol.acksIn(responses)) {
                    commands.markDone(acked);
                }
                List<Long> applied = listener.onResponses(c.worldId(), responses);
                if (!applied.isEmpty()) {
                    send(c, protocol.acks(applied));
                }
            }
        } catch (IOException e) {
            if (c.isOpen()) {
                log.warn("{}: world connection lost: {}", name, e.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("{}: unexpected failure in the world reader; dropping the connection", name, e);
        } finally {
            c.close();
            if (connection == c) {
                connection = null;
            }
        }
    }

    /** Safe from several threads: a message is never interleaved with another. */
    private void send(Connection c, MessageLite message) throws IOException {
        synchronized (c.out()) {
            message.writeDelimitedTo(c.out());
            c.out().flush();
        }
    }
}
