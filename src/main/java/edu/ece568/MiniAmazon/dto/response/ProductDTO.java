package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.Product;
import lombok.Data;

@Data
public class ProductDTO {
    private Long id;
    private String name;
    private String productID;
    private double price;
    private String description;
    private int quantity;
    private String category;
    private WarehouseDTO warehouse;
    
    // Static factory method for entity conversion
    public static ProductDTO fromEntity(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setProductID(product.getProductID());
        dto.setPrice(product.getPrice());
        dto.setDescription(product.getDescription());
        dto.setQuantity(product.getQuantity());
        dto.setCategory(product.getCategory());
        
        if (product.getWareHouse() != null) {
            dto.setWarehouse(WarehouseDTO.fromEntity(product.getWareHouse()));
        }
        
        return dto;
    }
} 