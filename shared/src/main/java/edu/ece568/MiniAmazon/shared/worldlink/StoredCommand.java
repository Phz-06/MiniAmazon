package edu.ece568.MiniAmazon.shared.worldlink;

/** A command for the world as it sits in world_command. id is the protocol sequence number. */
public record StoredCommand(long id, String type, Long refId, byte[] payload, int attempts) {
}
