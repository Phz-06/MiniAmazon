package edu.ece568.MiniAmazon.Listener;

import edu.ece568.MiniAmazon.Event.PackOrderEvent;
import edu.ece568.MiniAmazon.Event.ProcessPaymentEvent;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.service.EventPublisher;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentEventListener {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final EventPublisher eventPublisher;
    
    // Default warehouse ID - in a real system, you'd determine this dynamically
    private static final int DEFAULT_WAREHOUSE_ID = 1;

    public PaymentEventListener(PaymentService paymentService, OrderService orderService, EventPublisher eventPublisher) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void handlePaymentEvent(ProcessPaymentEvent event) throws AccessDeniedException {
        log.info("Received PaymentEvent: {} - {}$", event.getOrderId(), event.getAmount());
        
        // Process the payment
        paymentService.processPayment(event.getOrderId());
        log.info("Payment processed for order id {}", event.getOrderId());
        
        // Get the order details
        Order order = orderService.getOrder(event.getOrderId());
        
        // Generate a unique package ID
        String packageId = "PKG-" + UUID.randomUUID().toString().substring(0, 8);
        order.setStatus(Status.PACKING);
        order.setShipmentId(packageId);
        orderService.saveOrder(order);
        log.info("Order status updated to PACKING for order id {}", event.getOrderId());
        // Convert order items to the format expected by the packing service
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("product_id", item.getProduct().getId());
            itemMap.put("description", item.getProduct().getName());
            itemMap.put("quantity", item.getQuantity());
            items.add(itemMap);
        }
        
        // Get delivery coordinates (x, y)
        int destX = order.getDeliveryX();
        int destY = order.getDeliveryY();
        
        // Get UPS account if available (this should be added to your Order model)
        // For now, we'll use null as a placeholder
        String upsAccount = null;
        
        // Create and publish the pack order event
        PackOrderEvent packOrderEvent = new PackOrderEvent(
                order.getId(),
                DEFAULT_WAREHOUSE_ID,
                items,
                packageId,
                destX,
                destY,
                upsAccount
        );
        
        eventPublisher.publish(packOrderEvent);
        log.info("Published pack order event for order id: {}, package id: {}", order.getId(), packageId);
    }
}
