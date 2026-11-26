package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.Event.CancelOrderEvent;
import edu.ece568.MiniAmazon.Event.ConfirmOrderEvent;
import edu.ece568.MiniAmazon.Event.ProcessPaymentEvent;
import edu.ece568.MiniAmazon.dto.request.ConfirmOrderRequest;
import edu.ece568.MiniAmazon.dto.request.OrderRequest;
import edu.ece568.MiniAmazon.dto.request.PaymentRequest;
import edu.ece568.MiniAmazon.dto.response.OrderDTO;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.service.EventPublisher;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.AccessDeniedException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final EventPublisher eventPublisher;
    private final OrderService orderService;
    private final UserService userService;

    public OrderController(EventPublisher eventPublisher, OrderService orderService, UserService userService) {
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
        this.userService = userService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createOrder(@Valid @RequestBody OrderRequest order, @AuthenticationPrincipal UserDetails user) throws AccessDeniedException {
        log.info("Received order creation request for user ID: {} with user {}", order.getUserId(), user.getUsername());

        if (order.getUserId() != userService.getUserIdByUsername(user.getUsername())) {
            log.warn("User {} does not belong to user ID {}", user.getUsername(), order.getUserId());
            throw new AccessDeniedException("You are not authorized to create an order for this user.");
        }
        Order tempOrder = orderService.createOrder(order.getUserId(), order.getItems());

        Map<String, String> orderDetails = Map.of(
                "orderId", String.valueOf(tempOrder.getId()),
                "status", "success"
        );
        return ResponseEntity.accepted().body(orderDetails);
    }

    @PreAuthorize("@orderSecurity.isOrderOwner(#orderId)")
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

    @PreAuthorize("@orderSecurity.isOrderOwner(#orderId)")
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

    @PreAuthorize("@orderSecurity.isOrderOwner(#orderId)")
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

    @PreAuthorize("@orderSecurity.isOrderOwner(#orderId)")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable Long orderId) throws AccessDeniedException {
        OrderDTO orderDTO = OrderDTO.fromEntity(orderService.getOrder(orderId));
        return ResponseEntity.ok(orderDTO);
    }

    @PreAuthorize("@orderSecurity.isOrderOwner(#orderId)")
    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> getOrderStatus(@PathVariable Long orderId) throws AccessDeniedException {
        Order order = orderService.getOrder(orderId);
        Map<String, String> response = Map.of(
                "orderId", String.valueOf(orderId),
                "status", order.getStatus().name()
        );
        return ResponseEntity.ok(response);
    }

}
