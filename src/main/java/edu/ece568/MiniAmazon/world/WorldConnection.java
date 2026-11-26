package edu.ece568.MiniAmazon.world;

import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnect;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnected;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AInitWarehouse;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * One TCP connection to the world, after a successful handshake. Knows the
 * wire format and nothing else: no retries, no state about what was sent.
 *
 * Framing is a varint length followed by the message. Both streams are
 * created once and kept for the life of the connection. The previous code
 * built a new CodedInputStream per message; that class reads ahead into its
 * own buffer, so when two messages arrived in one TCP segment the second was
 * swallowed with the discarded buffer. parseDelimitedFrom reads exactly one
 * message's bytes from the shared stream.
 */
final class WorldConnection implements Closeable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final long worldId;

    private WorldConnection(Socket socket, InputStream in, OutputStream out, long worldId) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.worldId = worldId;
    }

    /**
     * @param worldId    world to rejoin, or null to create a new one
     * @param warehouses warehouses to create; only meaningful for a new world
     */
    static WorldConnection open(String host, int port, Long worldId, List<AInitWarehouse> warehouses,
                                int timeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            AConnect.Builder hello = AConnect.newBuilder().setIsAmazon(true).addAllInitwh(warehouses);
            if (worldId != null) {
                hello.setWorldid(worldId);
            }
            hello.build().writeDelimitedTo(out);
            out.flush();

            AConnected reply = AConnected.parseDelimitedFrom(in);
            if (reply == null) {
                throw new EOFException("World closed the connection during the handshake");
            }
            if (!"connected!".equals(reply.getResult())) {
                throw new WorldRejectedException(reply.getResult());
            }
            // From here on silence is normal: the world only speaks when something happens.
            socket.setSoTimeout(0);
            return new WorldConnection(socket, in, out, reply.getWorldid());
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    long worldId() {
        return worldId;
    }

    boolean isOpen() {
        return !socket.isClosed();
    }

    /** Safe to call from several threads; a message is never interleaved with another. */
    void send(ACommands commands) throws IOException {
        synchronized (out) {
            commands.writeDelimitedTo(out);
            out.flush();
        }
    }

    /** Blocks until the next message. Only the reader thread calls this. */
    AResponses read() throws IOException {
        AResponses responses = AResponses.parseDelimitedFrom(in);
        if (responses == null) {
            throw new EOFException("World closed the connection");
        }
        return responses;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing is best effort
        }
    }
}
