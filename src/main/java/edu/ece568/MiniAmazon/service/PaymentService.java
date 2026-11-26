package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.fulfillment.FulfillmentService;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
public class PaymentService {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final FulfillmentService fulfillment;

    public PaymentService(OrderService orderService, OrderRepository orderRepository, FulfillmentService fulfillment) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.fulfillment = fulfillment;
    }

    /**
     * The amount comes from the order, not from the client: the request's
     * amount is only checked against it. Exactly one caller wins
     * CONFIRMED -> PAID, so a retried or double-submitted payment cannot
     * start fulfilment twice. The pack command is written to the outbox in
     * this same transaction: there is no moment where an order is paid but
     * nothing will ever pack it.
     */
    @Transactional
    public void processPayment(long orderId, BigDecimal amount) {
        Order order = orderService.getOrder(orderId);
        if (amount == null || order.getTotalPrice().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Payment amount does not match the order total");
        }
        if (orderRepository.transition(orderId, Status.CONFIRMED, Status.PAID) == 0) {
            throw new IllegalStateException("Order " + orderId + " is not awaiting payment");
        }
        fulfillment.startPacking(orderId);
        log.info("Payment processed for order id {}", orderId);
    }
}
