package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.service.ProductService;
import java.io.*;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CatalogInitializer {
    private final ProductService productService;

    public CatalogInitializer(ProductService productService) {
        this.productService = productService;
    }

    public void run() {
        // Create products from the CSV file
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("catalog.csv"))));
            String line;
            boolean isFirst = true;
            while ((line = reader.readLine()) != null) {
                if (isFirst) {
                    isFirst = false; // Skip header
                    continue;
                }
                String[] parts = line.split(",", -1);

                String name = parts[0].trim();
                String productID = parts[1].trim();
                BigDecimal price = new BigDecimal(parts[2].trim());
                String description = parts[3].trim();
                int quantity = Integer.parseInt(parts[4].trim());
                String category = parts[5].trim();
                Long wareHouseId = Long.parseLong(parts[6].trim());

                try {
                    productService.createProduct(name, productID, price, description, quantity, category, wareHouseId);
                    log.info("Created product: {}", productID);
                } catch (Exception e) {
                    log.warn("Skipped product {}: {}", productID, e.getMessage());
                }
            }
            log.info("Created default catalog successfully");
        } catch (Exception e) {
            log.warn("Failed to create the initial catalog: {}", e.getMessage());
        }
    }
}

