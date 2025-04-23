package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ProcessPaymentEvent extends DomainEvent {
    private final long orderId;
    private final BigDecimal amount;
    private final String creditCardNumber;

    public ProcessPaymentEvent(long orderId, BigDecimal amount, String creditCardNumber) {
        super();
        this.orderId = orderId;
        this.amount = amount;
        this.creditCardNumber = creditCardNumber;
    }
}
