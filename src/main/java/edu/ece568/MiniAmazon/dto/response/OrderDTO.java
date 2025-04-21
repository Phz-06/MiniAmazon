package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Status;
import lombok.Data;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class OrderDTO {
    private Long id;
    private List<OrderItemDTO> orderItems;
    private int deliveryX;
    private int deliveryY;
    private Status status;
    private String upsAccountName;
    
    public static OrderDTO fromEntity(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setDeliveryX(order.getDeliveryX());
        dto.setDeliveryY(order.getDeliveryY());
        dto.setStatus(order.getStatus());
        dto.setUpsAccountName(order.getUpsAccountName());
        
        if (order.getOrderItems() != null) {
            dto.setOrderItems(order.getOrderItems().stream()
                .map(OrderItemDTO::fromEntity)
                .collect(Collectors.toList()));
        }
        
        return dto;
    }
} 