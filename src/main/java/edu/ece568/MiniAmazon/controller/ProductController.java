package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.dto.request.ProductUpdateRequest;
import edu.ece568.MiniAmazon.dto.response.CategoryListResponse;
import edu.ece568.MiniAmazon.dto.response.ProductDTO;
import edu.ece568.MiniAmazon.dto.response.ProductListResponse;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*") // Allow requests from any origin for the frontend
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get all available products
     * 
     * @return List of all products with quantity > 0
     */
    @GetMapping
    public ResponseEntity<ProductListResponse> getAllProducts() {
        List<Product> products = productService.getAllAvailableProducts();
        return ResponseEntity.ok(ProductListResponse.fromProducts(products));
    }
    
    /**
     * Get products by category
     * 
     * @param category The category to filter by
     * @return List of products in the specified category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ProductListResponse> getProductsByCategory(@PathVariable String category) {
        List<Product> products = productService.getProductsByCategory(category);
        return ResponseEntity.ok(ProductListResponse.fromProducts(products));
    }
    
    /**
     * Get all available product categories
     * 
     * @return List of all distinct product categories
     */
    @GetMapping("/categories")
    public ResponseEntity<CategoryListResponse> getAllCategories() {
        List<String> categories = productService.getAllCategories();
        return ResponseEntity.ok(CategoryListResponse.fromCategories(categories));
    }
    
    /**
     * Get a single product by its ID
     * 
     * @param id The product ID
     * @return The product details
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {
        Product product = productService.getProduct(id);
        return ResponseEntity.ok(ProductDTO.fromEntity(product));
    }
    
    /**
     * Update a product's price and category (admin only)
     * Other properties are controlled by the world simulator and cannot be modified
     * 
     * @param id The product ID
     * @param request The update request with new price and/or category
     * @return The updated product details
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        
        Product updatedProduct = productService.updateProductPriceAndCategory(
                id, 
                request.getPrice(), 
                request.getCategory()
        );
        
        return ResponseEntity.ok(ProductDTO.fromEntity(updatedProduct));
    }
} 