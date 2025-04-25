package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductUpdateRequest {
    @Min(value = 0, message = "Price must be non-negative")
    private BigDecimal price;
    
    private String category;
} 