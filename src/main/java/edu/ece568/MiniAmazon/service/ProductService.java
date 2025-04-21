package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.WareHouse;
import edu.ece568.MiniAmazon.repository.ProductRepository;
import edu.ece568.MiniAmazon.repository.WareHouseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final WareHouseRepository wareHouseRepository;

    public ProductService(ProductRepository productRepository, WareHouseRepository wareHouseRepository) {
        this.productRepository = productRepository;
        this.wareHouseRepository = wareHouseRepository;
    }

    public int getProductCount(String productID) {
        return productRepository.findQuantityByProductID(productID);
    }
    
    /**
     * Creates a new product and associates it with a warehouse
     * 
     * @param name Product name
     * @param productID Unique product identifier
     * @param price Product price
     * @param description Product description
     * @param quantity Initial stock quantity
     * @param category Product category
     * @param wareHouseId ID of the warehouse where the product is stored
     * @return The created Product entity
     * @throws IllegalArgumentException if the warehouse doesn't exist or if the productID already exists
     */
    @Transactional
    public Product createProduct(String name, String productID, double price, 
                                String description, int quantity, String category, 
                                Long wareHouseId) {
        try {
            // Check if a product with the same ID already exists
            if (productRepository.findByProductID(productID) != null) {
                throw new IllegalArgumentException("Product with ID " + productID + " already exists");
            }

            // Find the warehouse
            WareHouse wareHouse = wareHouseRepository.findById(wareHouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse with ID " + wareHouseId + " not found"));

            // Create and populate the product
            Product product = new Product();
            product.setName(name);
            product.setProductID(productID);
            product.setPrice(price);
            product.setDescription(description);
            product.setQuantity(quantity);
            product.setCategory(category);
            product.setWareHouse(wareHouse);

            // Save and return the product
            return productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Product with ID " + productID + " already exists");        }
    }

    /**
     * Updates the stock quantity of a product
     *
     * @param productID Unique product identifier
     * @param quantity New stock quantity
     * @return The updated Product entity
     * @throws IllegalArgumentException if the product doesn't exist
     */
    @Transactional
    public Product updateProductQuantity(String productID, int quantity) {
        // Find the product
        Product product = productRepository.findByProductID(productID);
        if (product == null) {
            throw new IllegalArgumentException("Product with ID " + productID + " not found");
        }

        // Update the quantity
        product.setQuantity(quantity);

        // Save and return the updated product
        return productRepository.save(product);
    }
    
}
