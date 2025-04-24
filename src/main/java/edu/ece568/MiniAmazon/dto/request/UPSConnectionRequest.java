package edu.ece568.MiniAmazon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for UPS connection requests
 */
@Getter
@Setter
public class UPSConnectionRequest {
    
    @NotBlank(message = "Host must be provided")
    private String host = "localhost";
    
    @Min(value = 1, message = "Port must be a positive number")
    private int port = 8001;
    
    // Default constructor
    public UPSConnectionRequest() {
        // Default values set in field initialization
    }
    
    // Constructor with parameters
    public UPSConnectionRequest(String host, int port) {
        this.host = host;
        this.port = port;
    }
} 