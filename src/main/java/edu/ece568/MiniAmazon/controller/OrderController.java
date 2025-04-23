package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.Event.CancelOrderEvent;
import edu.ece568.MiniAmazon.Event.ConfirmOrderEvent;
import edu.ece568.MiniAmazon.Event.CreateOrderEvent;
import edu.ece568.MiniAmazon.Event.ProcessPaymentEvent;
import edu.ece568.MiniAmazon.dto.request.ConfirmOrderRequest;
import edu.ece568.MiniAmazon.dto.request.OrderRequest;
import edu.ece568.MiniAmazon.dto.request.PaymentRequest;
import edu.ece568.MiniAmazon.dto.response.OrderDTO;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.service.EventPublisher;
import edu.ece568.MiniAmazon.service.OrderService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createOrder(@Valid @RequestBody OrderRequest order) {
        log.info("Received order creation request for user ID: {}", order.getUserId());
        Order tempOrder = orderService.createOrder(order.getUserId(), order.getItems());

        Map<String, String> orderDetails = Map.of(
                "orderId", String.valueOf(tempOrder.getId()),
                "status", "success"
        );
        eventPublisher.publish(new CreateOrderEvent(order.getUserId(), order.getItems()));
        return ResponseEntity.accepted().body(orderDetails);
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<Map<String, String>> confirmOrder(
            @PathVariable Long orderId,
            @Valid @RequestBody ConfirmOrderRequest request) {
        log.info("Received order confirmation request for order ID: {} to {},{}",
                orderId, request.getX(), request.getY());

        eventPublisher.publish(new ConfirmOrderEvent(orderId, request.getX(), request.getY()));

        Map<String, String> response = Map.of(
                "orderId", String.valueOf(orderId),
                "status", "confirmation_in_progress"
        );

        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, String>> cancelOrder(@PathVariable Long orderId) {
        log.info("Received order cancellation request for order ID: {}", orderId);
        eventPublisher.publish(new CancelOrderEvent(orderId));

        Map<String, String> response = Map.of(
                "orderId", String.valueOf(orderId),
                "status", "cancellation_in_progress"
        );

        return ResponseEntity.accepted().body(response);
    }

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<Map<String, String>> processPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentRequest request) {
        log.info("Received payment request for order ID: {}", orderId);

        eventPublisher.publish(new ProcessPaymentEvent(orderId, request.getAmount(), request.getCreditCardNumber()));
        Map<String, String> response = Map.of(
                "orderId", String.valueOf(orderId),
                "status", "payment_processing"
        );

        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable Long orderId) {
        OrderDTO orderDTO = OrderDTO.fromEntity(orderService.getOrder(orderId));
        return ResponseEntity.ok(orderDTO);
    }


}
