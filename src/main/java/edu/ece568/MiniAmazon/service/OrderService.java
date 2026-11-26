package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.dto.request.OrderItemRequest;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import edu.ece568.MiniAmazon.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Slf4j
public class OrderService {

    private final ProductService productService;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;

    public OrderService(ProductService productService, OrderRepository orderRepository, InventoryService inventoryService, UserRepository userRepository) {
        this.productService = productService;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.userRepository = userRepository;
    }

    @Transactional
    public Order createOrder(long userId, List<OrderItemRequest> items) {
        User user = userRepository.findById(userId).orElseThrow(() ->
                new IllegalArgumentException("User with ID " + userId + " not found"));

        // One line per product, however the client split them.
        Map<String, Integer> quantities = new TreeMap<>();
        for (OrderItemRequest item : items) {
            quantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        Order order = new Order();
        order.setUser(user);
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(Status.CREATED);

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> line : quantities.entrySet()) {
            Product product = productService.getProductById(line.getKey());
            if (product == null) {
                // An order that silently drops a line is worse than no order.
                throw new IllegalArgumentException("Product with ID " + line.getKey() + " not found");
            }
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(line.getValue());
            orderItem.setPrice(product.getPrice());
            orderItems.add(orderItem);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(line.getValue())));
        }
        order.setOrderItems(orderItems);
        order.setTotalPrice(total);
        orderRepository.save(order);
        log.info("Order created with ID: {}", order.getId());
        return order;
    }

    /**
     * CREATED -> CONFIRMED and reserve stock, atomically. If any line is short
     * the whole transaction rolls back, so the order is CREATED again and no
     * stock was taken.
     */
    @Transactional
    public void confirmOrder(long orderId, int x, int y) {
        if (orderRepository.transition(orderId, Status.CREATED, Status.CONFIRMED) == 0) {
            throw new IllegalStateException("Order " + orderId + " is not awaiting confirmation");
        }
        Order order = getOrder(orderId);
        inventoryService.lockInventory(order.getOrderItems());
        order.setDeliveryX(x);
        order.setDeliveryY(y);
    }

    /**
     * Only the request that wins the CONFIRMED -> CANCELED transition gives the
     * stock back, so a double click cannot return it twice. A CREATED order
     * never reserved anything. Anything later is already with the warehouse.
     */
    @Transactional
    public void cancelOrder(long orderId) {
        if (orderRepository.transition(orderId, Status.CONFIRMED, Status.CANCELED) == 1) {
            inventoryService.unlockInventory(getOrder(orderId).getOrderItems());
            return;
        }
        if (orderRepository.transition(orderId, Status.CREATED, Status.CANCELED) == 1) {
            return;
        }
        throw new IllegalStateException("Order " + orderId + " can no longer be canceled");
    }

    @Transactional(readOnly = true)
    public Order getOrder(long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order with ID " + orderId + " not found"));
    }

    @Transactional
    public void setStatus(String shipmentId, Status status) {
        Order order = getOrderByShipmentId(shipmentId);
        order.setStatus(status);
        orderRepository.save(order);
    }

    @Transactional
    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrderByShipmentId(String shipmentId) {
        return orderRepository.findOrderByShipmentId(shipmentId).orElseThrow(() -> new IllegalArgumentException("Order with shipment ID " + shipmentId + " not found"));
    }
}
