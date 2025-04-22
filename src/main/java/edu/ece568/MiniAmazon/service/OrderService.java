package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.dto.request.OrderItemRequest;
import edu.ece568.MiniAmazon.dto.request.OrderRequest;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderService {


    private final ProductService productService;
    private final OrderRepository orderRepository;

    public OrderService(ProductService productService, OrderRepository orderRepository) {
        this.productService = productService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(long userId, List<OrderItemRequest> items) {
        Order order = new Order();
        order.setUserId(userId);
        order.setCreatedAt(LocalDateTime.now());
        List<OrderItem> orderItemList = new ArrayList<>();
        for (OrderItemRequest item : items) {
            Product product = productService.getProductById(item.getProductId());
            if (product == null) {
                log.error("Product with ID {} not found", item.getProductId());
                continue;
            }
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setOrder(order);
            orderItemList.add(orderItem);
        }
        order.setOrderItems(orderItemList);
        order.setStatus(Status.CREATED);
        orderRepository.save(order);
        log.info("Order created with ID: {}", order.getId());

        return order;
    }

    @Transactional
    public void preparePayment() {

    }
}
