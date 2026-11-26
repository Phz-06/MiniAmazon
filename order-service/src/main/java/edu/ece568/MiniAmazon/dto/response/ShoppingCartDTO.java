package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.ShoppingCart;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ShoppingCartDTO {
    private Long id;
    private Long userId;
    private List<CartItemDTO> cartItems;
    private BigDecimal totalPrice;
    private int totalQuantity;
    
    public static ShoppingCartDTO fromEntity(ShoppingCart cart) {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setId(cart.getId());
        dto.setUserId(cart.getUser().getId());
        dto.setTotalPrice(cart.getTotalPrice());
        dto.setTotalQuantity(cart.getTotalQuantity());
        
        if (cart.getCartItems() != null) {
            dto.setCartItems(cart.getCartItems().stream()
                .map(CartItemDTO::fromEntity)
                .collect(Collectors.toList()));
        }
        
        return dto;
    }
} 