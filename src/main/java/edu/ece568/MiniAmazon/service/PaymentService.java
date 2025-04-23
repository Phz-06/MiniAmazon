package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentService {
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public PaymentService(OrderService orderService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void processPayment(long orderId) {
        Order order = orderService.getOrder(orderId);
        order.setStatus(Status.PAID);
        log.info("Payment processed for order id {}", orderId);
        orderRepository.save(order);
    }
}
