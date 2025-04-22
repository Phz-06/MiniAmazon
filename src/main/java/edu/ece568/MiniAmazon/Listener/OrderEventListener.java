package edu.ece568.MiniAmazon.Listener;

import edu.ece568.MiniAmazon.Event.CreateOrderEvent;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderEventListener {
    private final OrderService orderService;

    public OrderEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @EventListener
    public void handleCreateOrderEvent(CreateOrderEvent event) {
        log.info("Received CreateOrderEvent: {} - {}", event.getUserId(), event.getOrderItems());
        Order order = orderService.createOrder(event.getUserId(), event.getOrderItems());
    }

}

