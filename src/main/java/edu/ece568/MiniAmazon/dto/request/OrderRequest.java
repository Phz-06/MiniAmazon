package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;
    
    @NotNull(message = "Delivery X-coordinate is required")
    private Integer deliveryX;
    
    @NotNull(message = "Delivery Y-coordinate is required")
    private Integer deliveryY;
    
    private String upsAccountName; // Optional
} 