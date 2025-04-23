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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderService {


    private final ProductService productService;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    public OrderService(ProductService productService, OrderRepository orderRepository, InventoryService inventoryService) {
        this.productService = productService;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Order createOrder(long userId, List<OrderItemRequest> items) {
        Order order = new Order();
        order.setUserId(userId);
        order.setCreatedAt(LocalDateTime.now());
        List<OrderItem> orderItemList = new ArrayList<>();
        BigDecimal price = BigDecimal.ZERO;
        for (OrderItemRequest item : items) {
            Product product = productService.getProductById(item.getProductId());
            if (product == null) {
                log.error("Product with ID {} not found", item.getProductId());
                continue;
            }
            price = price.add(product.getPrice());
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setOrder(order);
            orderItemList.add(orderItem);
        }
        order.setTotalPrice(price);
        order.setOrderItems(orderItemList);
        order.setStatus(Status.CREATED);
        orderRepository.save(order);
        log.info("Order created with ID: {}", order.getId());

        return order;
    }

    public void confirmOrder(long orderId, int x, int y) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order with ID " + orderId + " not found"));

        if (order.getStatus() != Status.CREATED) {
            throw new IllegalStateException("Order has already been confirmed or processed");
        }
        try {
            inventoryService.lockInventory(order.getOrderItems());
            order.setStatus(Status.CONFIRMED);
            order.setDeliveryX(x);
            order.setDeliveryY(y);
            orderRepository.save(order);
        } catch (Exception e) {
            log.error("Failed to lock inventory for order ID: {}", orderId, e);
            throw new RuntimeException("Failed to lock inventory for order ID: " + orderId, e);
        }
    }

    public void cancelOrder(long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order with ID " + orderId + " not found"));


        try {
            if (order.getStatus() == Status.CONFIRMED) {

                inventoryService.unlockInventory(order.getOrderItems());
                order.setStatus(Status.CANCELED);
                orderRepository.save(order);
            }
        } catch (Exception e) {
            log.error("Failed to unlock inventory for order ID: {}", orderId, e);
            throw new RuntimeException(e);
        }
    }

    public Order getOrder(long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order with ID " + orderId + " not found"));
    }


}
