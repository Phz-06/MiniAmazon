package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.WareHouse;
import edu.ece568.MiniAmazon.repository.ProductRepository;
import edu.ece568.MiniAmazon.repository.WareHouseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Service
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final WareHouseRepository wareHouseRepository;

    public ProductService(ProductRepository productRepository, WareHouseRepository wareHouseRepository) {
        this.productRepository = productRepository;
        this.wareHouseRepository = wareHouseRepository;
    }

    public int getProductCount(String productID) {
        return productRepository.findQuantityByProductId(productID);
    }

    public Product getProductById(String productID) {
        return productRepository.findByProductId(productID);
    }

    public Product getProduct(long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product with ID " + productId + " not found"));
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
    public Product createProduct(String name, String productID, BigDecimal price,
                                String description, int quantity, String category, 
                                Long wareHouseId) {
        try {
            // Check if a product with the same ID already exists
            if (productRepository.findByProductId(productID) != null) {
                throw new IllegalArgumentException("Product with ID " + productID + " already exists");
            }

            // Find the warehouse
            WareHouse wareHouse = wareHouseRepository.findById(wareHouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse with ID " + wareHouseId + " not found"));

            // Create and populate the product
            Product product = new Product();
            product.setName(name);
            product.setProductId(productID);
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
        Product product = productRepository.findByProductId(productID);
        if (product == null) {
            throw new IllegalArgumentException("Product with ID " + productID + " not found");
        }

        // Update the quantity
        product.setQuantity(quantity);

        // Save and return the updated product
        return productRepository.save(product);
    }

    /**
     * Increase inventory for a product that arrived from the world simulator
     * 
     * @param productId The ID of the product (from world simulator)
     * @param description The description of the product
     * @param count The quantity to add
     * @param warehouseId The ID of the warehouse (from world simulator)
     */
    @Transactional
    public void increaseInventory(long productId, String description, int count, int warehouseId) {
        log.info("Increasing inventory for product {} by {} in warehouse {}", productId, count, warehouseId);
        
        // Convert the world simulator IDs to our database IDs
        String productStrId = String.valueOf(productId);
        
        // Find the warehouse by ID
        log.info("Looking for warehouse with ID: {}", warehouseId);
        WareHouse warehouse = wareHouseRepository.findById((long) warehouseId)
            .orElseGet(() -> {
                // If warehouse doesn't exist, create it
                log.info("Warehouse {} not found in database, creating new warehouse", warehouseId);
                WareHouse newWarehouse = new WareHouse();
                newWarehouse.setName("Warehouse " + warehouseId);
                newWarehouse.setXCoordinate(0);
                newWarehouse.setYCoordinate(0);
                return wareHouseRepository.save(newWarehouse);
            });
        
        log.info("Using warehouse: ID={}, name={}, coordinates=({},{})", 
                warehouse.getId(), warehouse.getName(), 
                warehouse.getXCoordinate(), warehouse.getYCoordinate());
        
        // Find or create the product
        Product product = productRepository.findByProductId(productStrId);
        if (product == null) {
            // Create new product if it doesn't exist
            log.info("Creating new product {} ({})", productStrId, description);
            product = new Product();
            product.setProductId(productStrId);
            product.setName(description);
            product.setDescription(description);
            product.setPrice(BigDecimal.ONE); // Default price
            product.setQuantity(count);
            product.setCategory("General"); // Default category
            product.setWareHouse(warehouse);
        } else {
            // Update existing product
            log.info("Updating existing product {} from {} to {}", 
                    productStrId, product.getQuantity(), product.getQuantity() + count);
            product.setQuantity(product.getQuantity() + count);
        }
        
        // Save the product
        productRepository.save(product);
    }
}
