package edu.ece568.MiniAmazon.Event;

import edu.ece568.MiniAmazon.dto.request.OrderItemRequest;
import edu.ece568.MiniAmazon.model.OrderItem;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateOrderEvent extends DomainEvent {
    private final long userId;
    private final List<OrderItemRequest> orderItems;

    public CreateOrderEvent(long userId, List<OrderItemRequest> orderItems) {
        super();
        this.userId = userId;
        this.orderItems = orderItems;
    }
}
