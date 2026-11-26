package edu.ece568.MiniAmazon.Listener;

import edu.ece568.MiniAmazon.Event.CancelOrderEvent;
import edu.ece568.MiniAmazon.Event.ConfirmOrderEvent;
import edu.ece568.MiniAmazon.Event.CreateOrderEvent;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OrderEventListener {
    private final OrderService orderService;

    public OrderEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @Transactional
    @EventListener
    public void handleCreateOrderEvent(CreateOrderEvent event) {
        log.info("Received CreateOrderEvent: {} - {}", event.getUserId(), event.getOrderItems());
        Order order = orderService.createOrder(event.getUserId(), event.getOrderItems());

    }

    // These listeners run synchronously on the request thread, so a failure
    // (out of stock, wrong state) propagates to the controller and becomes a
    // 409 instead of a 202 for something that never happened.
    @EventListener
    public void handleConfirmOrderEvent(ConfirmOrderEvent event) {
        log.info("Received ConfirmOrderEvent: {} - {}", event.getEventId(), event.getOrderId());
        orderService.confirmOrder(event.getOrderId(), event.getX(), event.getY());
    }

    @EventListener
    public void handleCancelOrderEvent(CancelOrderEvent event) {
        log.info("Received CancelOrderEvent: {} - {}", event.getEventId(), event.getOrderId());
        orderService.cancelOrder(event.getOrderId());
    }
}

