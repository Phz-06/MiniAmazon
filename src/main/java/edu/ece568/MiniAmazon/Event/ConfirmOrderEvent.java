package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

@Getter
public class ConfirmOrderEvent extends DomainEvent {
    private final long orderId;
    private final int x;
    private final int y;

    public ConfirmOrderEvent(long orderId, int x, int y) {
        super();
        this.orderId = orderId;
        this.x = x;
        this.y = y;
    }
}
