package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for UPS server configuration requests
 */
@Getter
@Setter
public class UPSServerRequest {
    
    @Min(value = 1, message = "Port must be a positive number")
    private Integer port;
    
    // Default constructor
    public UPSServerRequest() {
        // Default constructor
    }
    
    // Constructor with parameters
    public UPSServerRequest(Integer port) {
        this.port = port;
    }
} 