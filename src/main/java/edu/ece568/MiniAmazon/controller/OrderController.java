package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.Event.CreateOrderEvent;
import edu.ece568.MiniAmazon.dto.request.OrderRequest;
import edu.ece568.MiniAmazon.service.EventPublisher;
import edu.ece568.MiniAmazon.service.OrderService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final EventPublisher eventPublisher;
    private final OrderService orderService;

    public OrderController(EventPublisher eventPublisher, OrderService orderService) {
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<String> createOrder(@Valid @RequestBody OrderRequest order) {
        log.info("Received order creation request for user ID: {}", order.getUserId());
        eventPublisher.publish(new CreateOrderEvent(order.getUserId(), order.getItems()));
        return ResponseEntity.ok("Order creation in progress");
    }
}
