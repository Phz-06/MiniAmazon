package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.OrderItem;
import lombok.Data;

@Data
public class OrderItemDTO {
    private Long id;
    private int quantity;
    private double price;
    private ProductSummaryDTO product;
    
    public static OrderItemDTO fromEntity(OrderItem orderItem) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(orderItem.getId());
        dto.setQuantity(orderItem.getQuantity());
        dto.setPrice(orderItem.getPrice());
        
        if (orderItem.getProduct() != null) {
            dto.setProduct(ProductSummaryDTO.fromEntity(orderItem.getProduct()));
        }
        
        return dto;
    }
} 