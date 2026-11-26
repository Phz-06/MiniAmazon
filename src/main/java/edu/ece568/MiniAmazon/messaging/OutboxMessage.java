package edu.ece568.MiniAmazon.messaging;

public record OutboxMessage(long id, String type, Long orderId, byte[] payload, int attempts) {
}
