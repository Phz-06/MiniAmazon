package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.model.WareHouse;
import edu.ece568.MiniAmazon.repository.WareHouseRepository;
import edu.ece568.MiniAmazon.service.ProductService;
import java.io.*;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CatalogInitializer implements CommandLineRunner {
    private final ProductService productService;
    private final WareHouseRepository wareHouseRepository;

    public CatalogInitializer(ProductService productService, WareHouseRepository wareHouseRepository) {
        this.productService = productService;
        this.wareHouseRepository = wareHouseRepository;
    }

    @Override
    public void run(String... args) {
        // Wait until WorldService finished
        Long warehouseId = 1L;
        WareHouse warehouse = null;
        int attempts = 0;
        while (warehouse == null && attempts < 10) {
            warehouse = wareHouseRepository.findById(warehouseId).orElse(null);
            if (warehouse == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                attempts++;
            }
        }
        if (warehouse == null) {
            log.error("Warehouse ID {} was never initialized. Skipping catalog creation.", warehouseId);
            return;
        }

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

                try {
                    productService.createProduct(name, productID, price, description, quantity, category, warehouseId);
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

