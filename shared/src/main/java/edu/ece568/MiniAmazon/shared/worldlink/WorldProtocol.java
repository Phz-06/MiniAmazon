package edu.ece568.MiniAmazon.shared.worldlink;

import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The two dialects of the world protocol (warehouse side, carrier side) have
 * the same shape and different message types. This is the part that differs.
 *
 * @param <C> the commands message we send
 * @param <R> the responses message we receive
 */
public interface WorldProtocol<C extends MessageLite, R extends MessageLite> {

    record Handshake(long worldId, String result) {
    }

    /**
     * @param worldId    world to join, or null to create one
     * @param initialise also register our warehouses or trucks; the world
     *                   refuses a second registration of the same ids
     */
    MessageLite hello(Long worldId, boolean initialise);

    /** @return null if the stream ended */
    Handshake readHandshake(InputStream in) throws IOException;

    /** @return null if the stream ended */
    R readResponses(InputStream in) throws IOException;

    /** The world's acknowledgements of our commands. */
    List<Long> acksIn(R responses);

    /** One message carrying all the given commands; unreadable ones are reported, not sent. */
    C batch(List<StoredCommand> commands, BiConsumer<StoredCommand, String> unreadable);

    C acks(List<Long> seqnums);

    C simSpeed(int speed);
}
