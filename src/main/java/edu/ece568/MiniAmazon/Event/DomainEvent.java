package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class DomainEvent {
    private final UUID eventId;
    private final LocalDateTime eventTime;

    public DomainEvent() {
        this.eventId = UUID.randomUUID();
        this.eventTime = LocalDateTime.now();
    }
}
