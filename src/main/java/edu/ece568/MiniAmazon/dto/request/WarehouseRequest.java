package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WarehouseRequest {
    @NotBlank(message = "Name is required")
    private String name;
    
    @NotNull(message = "X-coordinate is required")
    private Integer xCoordinate;
    
    @NotNull(message = "Y-coordinate is required")
    private Integer yCoordinate;
} 