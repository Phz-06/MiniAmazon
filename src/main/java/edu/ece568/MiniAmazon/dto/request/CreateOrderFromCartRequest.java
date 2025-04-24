package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderFromCartRequest {
    @NotNull(message = "Delivery X-coordinate is required")
    private Integer deliveryX;
    
    @NotNull(message = "Delivery Y-coordinate is required")
    private Integer deliveryY;
    
    private String upsAccountName; // Optional
} 