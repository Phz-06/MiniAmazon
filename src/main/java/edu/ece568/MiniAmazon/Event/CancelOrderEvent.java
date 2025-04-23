package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

@Getter
public class CancelOrderEvent extends DomainEvent {
    private final long orderId;

    public CancelOrderEvent(long orderId) {
        super();
        this.orderId = orderId;
    }
}
