package edu.ece568.MiniAmazon.world;

import java.io.IOException;

/** The simulator answered the handshake with something other than "connected!". */
public class WorldRejectedException extends IOException {
    public WorldRejectedException(String reply) {
        super("World refused the connection: " + reply);
    }
}
