package edu.ece568.MiniAmazon.shared.worldlink;

import java.io.IOException;

/** The simulator answered the handshake with something other than "connected!". */
public class WorldRejectedException extends IOException {
    private final String reply;

    public WorldRejectedException(String reply) {
        super("World refused the connection: " + reply);
        this.reply = reply;
    }

    public String reply() {
        return reply;
    }
}
