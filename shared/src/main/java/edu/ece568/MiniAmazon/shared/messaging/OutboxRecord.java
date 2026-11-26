package edu.ece568.MiniAmazon.shared.messaging;

public record OutboxRecord(long id, String topic, String key, byte[] payload, int attempts) {
}
