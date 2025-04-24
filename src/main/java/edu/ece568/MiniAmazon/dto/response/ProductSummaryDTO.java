package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.Product;
import lombok.Data;

@Data
public class ProductSummaryDTO {
    private Long id;
    private String name;
    private String productID;
    
    public static ProductSummaryDTO fromEntity(Product product) {
        ProductSummaryDTO dto = new ProductSummaryDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setProductID(product.getProductId());
        return dto;
    }
} 