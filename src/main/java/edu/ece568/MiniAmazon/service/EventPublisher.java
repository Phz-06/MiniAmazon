package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.Event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventPublisher {
    private final ApplicationEventPublisher eventPublisher;

    public EventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(DomainEvent event) {
        eventPublisher.publishEvent(event);
        log.info("Event published: {} - {}", event.getClass().getSimpleName(), event.getEventId());
    }
}
