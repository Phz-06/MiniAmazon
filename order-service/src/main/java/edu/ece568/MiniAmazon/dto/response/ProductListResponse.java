package edu.ece568.MiniAmazon.dto.response;

import edu.ece568.MiniAmazon.model.Product;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Data
public class ProductListResponse {
    private List<ProductDTO> products;
    private int count;
    
    public static ProductListResponse fromProducts(List<Product> products) {
        ProductListResponse response = new ProductListResponse();
        response.setProducts(products.stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList()));
        response.setCount(products.size());
        return response;
    }
    
    public static ProductListResponse fromProducts(Iterable<Product> products) {
        return fromProducts(
            StreamSupport.stream(products.spliterator(), false)
                .collect(Collectors.toList())
        );
    }
} 