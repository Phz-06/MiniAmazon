package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.CartItem;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemDTO {
    private Long id;
    private int quantity;
    private BigDecimal price;
    private ProductSummaryDTO product;
    
    public static CartItemDTO fromEntity(CartItem cartItem) {
        CartItemDTO dto = new CartItemDTO();
        dto.setId(cartItem.getId());
        dto.setQuantity(cartItem.getQuantity());
        dto.setPrice(cartItem.getPrice());
        
        if (cartItem.getProduct() != null) {
            dto.setProduct(ProductSummaryDTO.fromEntity(cartItem.getProduct()));
        }
        
        return dto;
    }
} 