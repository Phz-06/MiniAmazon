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
import java.util.List;

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
     * Get all available products (with quantity > 0)
     * 
     * @return List of available products
     */
    public List<Product> getAllAvailableProducts() {
        return productRepository.findAvailableProducts();
    }
    
    /**
     * Find products by category
     * 
     * @param category The category to search for
     * @return List of products in the specified category
     */
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }
    
    /**
     * Get all available product categories
     * 
     * @return List of all distinct product categories
     */
    public List<String> getAllCategories() {
        return productRepository.findAllCategories();
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

        productRepository.setProductQuantity(productID, quantity);
        product.setQuantity(quantity);
        return product;
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
            // Existing product: add to whatever is there now, in one statement.
            // Reading the quantity and saving it back would erase any purchase
            // that landed in between.
            productRepository.increaseProductQuantity(productStrId, count);
            return;
        }

        productRepository.save(product);
    }

    /**
     * Updates only the price and category of a product (admin only operation)
     * Other properties are controlled by the world simulator and should not be modified
     *
     * @param productId The ID of the product to update
     * @param price New price (can be null to keep existing price)
     * @param category New category (can be null to keep existing category)
     * @return The updated Product entity
     * @throws IllegalArgumentException if the product doesn't exist
     */
    @Transactional
    public Product updateProductPriceAndCategory(Long productId, BigDecimal price, String category) {
        // Find the product
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product with ID " + productId + " not found"));

        // Update only the allowed fields if they are not null
        if (price != null) {
            product.setPrice(price);
        }
        
        if (category != null && !category.isEmpty()) {
            product.setCategory(category);
        }

        // Save and return the updated product
        log.info("Admin updated product {} - Price: {}, Category: {}", 
                product.getProductId(), price, category);
        return productRepository.save(product);
    }
}
