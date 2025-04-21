package edu.ece568.MiniAmazon.dto.request;

import edu.ece568.MiniAmazon.model.Status;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PackageStatusUpdateRequest {
    @NotNull(message = "Status is required")
    private Status status;
    
    private String trackingNumber;
} 