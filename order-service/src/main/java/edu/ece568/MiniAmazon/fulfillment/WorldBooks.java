package edu.ece568.MiniAmazon.fulfillment;

import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.model.WareHouse;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import edu.ece568.MiniAmazon.repository.ProductRepository;
import edu.ece568.MiniAmazon.repository.WareHouseRepository;
import edu.ece568.MiniAmazon.service.ProductService;
import edu.ece568.MiniAmazon.shared.worldlink.WorldSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Keeps our stock figures and the warehouse telling the same story.
 *
 * The warehouse only packs what it has, and it only has what we bought. A new
 * world starts empty, so when the gateway reports one, every unit we believe
 * we own is turned back into a purchase: quantity drops to zero now and comes
 * back when the goods are reported arrived. The catalog file seeds products
 * the same way.
 */
@Slf4j
@Component
public class WorldBooks {

    private final WorldSession session;
    private final ProductRepository products;
    private final ProductService productService;
    private final WareHouseRepository warehouses;
    private final ReplenishmentService replenishment;
    private final OrderRepository orders;

    public WorldBooks(WorldSession session, ProductRepository products, ProductService productService,
                      WareHouseRepository warehouses, ReplenishmentService replenishment, OrderRepository orders) {
        this.orders = orders;
        this.session = session;
        this.products = products;
        this.productService = productService;
        this.warehouses = warehouses;
        this.replenishment = replenishment;
    }

    /** Part of the consumer's transaction: either we follow the gateway into the world and everything is on order, or nothing changed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void adopt(long worldId, long generation) {
        if (generation <= session.generation()) {
            return; // an announcement we already followed
        }
        boolean replacingAWorld = session.worldId() != null;
        session.save(worldId, generation);
        if (replacingAWorld) {
            // Their packages existed only in the world we lost. Nothing here can finish them;
            // they are reported so a person can refund or re-ship.
            long stranded = orders.countByStatusIn(List.of(Status.PACKING, Status.PACKED, Status.LOADING,
                    Status.LOADED, Status.DELIVERING));
            if (stranded > 0) {
                log.error("{} orders were in the warehouse or on a truck in the previous world and cannot be completed", stranded);
            }
        }
        WareHouse warehouse = warehouses.findById((long) FulfillmentService.WAREHOUSE_ID).orElseGet(() -> {
            WareHouse created = new WareHouse();
            created.setName("Warehouse " + FulfillmentService.WAREHOUSE_ID);
            created.setXCoordinate(10);
            created.setYCoordinate(20);
            return warehouses.save(created);
        });
        for (Product product : products.findAll()) {
            reorder(product.getProductId(), product.getName(), products.takeAllStock(product.getProductId()));
        }
        seedCatalog(warehouse.getId());
        log.info("Now in world {} (generation {}): stock is on order and becomes available as it arrives", worldId, generation);
    }

    private void seedCatalog(long warehouseId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("catalog.csv")),
                StandardCharsets.UTF_8))) {
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                String name = parts[0].trim();
                String productId = parts[1].trim();
                if (products.findByProductId(productId) != null) {
                    continue;
                }
                productService.createProduct(name, productId, new BigDecimal(parts[2].trim()), parts[3].trim(),
                        0, parts[5].trim(), warehouseId);
                reorder(productId, name, Integer.parseInt(parts[4].trim()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read catalog.csv", e);
        }
    }

    private void reorder(String productId, String name, int count) {
        if (count <= 0) {
            return;
        }
        try {
            replenishment.purchase(FulfillmentService.WAREHOUSE_ID, Long.parseLong(productId), name, count);
        } catch (NumberFormatException e) {
            log.warn("Product {} has a non-numeric id and cannot be ordered from the warehouse", productId);
        }
    }
}
