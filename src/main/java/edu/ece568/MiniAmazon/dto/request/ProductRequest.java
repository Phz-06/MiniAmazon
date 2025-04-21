package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    private String name;
    
    @NotBlank(message = "Product ID is required")
    private String productID;
    
    @Min(value = 0, message = "Price must be non-negative")
    private double price;
    
    @Size(max = 2000, message = "Description must be less than 2000 characters")
    private String description;
    
    @Min(value = 0, message = "Quantity must be non-negative")
    private int quantity;
    
    private String category;
    
    @NotNull(message = "Warehouse ID is required")
    private Long wareHouseId;
} 