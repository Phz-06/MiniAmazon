package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentService {
    private final OrderService orderService;

    public PaymentService(OrderService orderService) {
        this.orderService = orderService;
    }

    public void processPayment(long orderId) {
        Order order = orderService.getOrder(orderId);
        order.setStatus(Status.PAID);
        log.info("Payment processed for order id {}", orderId);
    }
}
