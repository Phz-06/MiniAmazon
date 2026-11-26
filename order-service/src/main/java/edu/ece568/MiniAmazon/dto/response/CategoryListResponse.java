package edu.ece568.MiniAmazon.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class CategoryListResponse {
    private List<String> categories;
    private int count;
    
    public static CategoryListResponse fromCategories(List<String> categories) {
        CategoryListResponse response = new CategoryListResponse();
        response.setCategories(categories);
        response.setCount(categories.size());
        return response;
    }
} 