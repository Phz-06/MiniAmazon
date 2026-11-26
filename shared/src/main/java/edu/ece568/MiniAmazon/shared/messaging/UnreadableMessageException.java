package edu.ece568.MiniAmazon.shared.messaging;

/** The record is not an Envelope. Retrying cannot fix that; it goes straight to the dead-letter topic. */
public class UnreadableMessageException extends RuntimeException {
    public UnreadableMessageException(Throwable cause) {
        super("Record is not a readable Envelope", cause);
    }
}
