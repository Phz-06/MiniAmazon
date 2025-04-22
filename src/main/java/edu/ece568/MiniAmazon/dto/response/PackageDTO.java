package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.AmazonPackage;
import edu.ece568.MiniAmazon.model.Status;
import lombok.Data;

@Data
public class PackageDTO {
    private Long id;
    private String trackingNumber;
    private Status status;
    private Long orderId;
    
    public static PackageDTO fromEntity(AmazonPackage pkg) {
        PackageDTO dto = new PackageDTO();
        dto.setId(pkg.getId());
        dto.setTrackingNumber(pkg.getShipmentID());
        dto.setStatus(pkg.getStatus());
        
        if (pkg.getOrder() != null) {
            dto.setOrderId(pkg.getOrder().getId());
        }
        
        return dto;
    }
} 