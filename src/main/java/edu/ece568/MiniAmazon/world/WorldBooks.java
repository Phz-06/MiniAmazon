package edu.ece568.MiniAmazon.world;

import edu.ece568.MiniAmazon.fulfillment.ReplenishmentService;
import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.WareHouse;
import edu.ece568.MiniAmazon.repository.ProductRepository;
import edu.ece568.MiniAmazon.repository.WareHouseRepository;
import edu.ece568.MiniAmazon.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Keeps our stock figures and the world's warehouse telling the same story.
 *
 * The world only packs what it has, and it only has what we bought from it.
 * A new world starts empty, so when we move to one, every unit we believe we
 * own is turned back into a purchase order: quantity drops to zero now and
 * comes back when the new world reports the goods arrived. The catalog file
 * seeds products the same way. The old code wrote the catalog quantities
 * straight into the database, which is why every pack request was refused.
 */
@Slf4j
@Component
public class WorldBooks {

    private final WorldSession session;
    private final Outbox outbox;
    private final ProductRepository products;
    private final ProductService productService;
    private final WareHouseRepository warehouses;
    private final ReplenishmentService replenishment;

    public WorldBooks(WorldSession session, Outbox outbox, ProductRepository products, ProductService productService,
                      WareHouseRepository warehouses, ReplenishmentService replenishment) {
        this.session = session;
        this.outbox = outbox;
        this.products = products;
        this.productService = productService;
        this.warehouses = warehouses;
        this.replenishment = replenishment;
    }

    /** One transaction: either we belong to the new world and everything is on order, or nothing changed. */
    @Transactional
    public void adopt(long worldId) {
        session.save(worldId, true);
        int stale = outbox.failAllPending(Destination.WORLD, "Superseded: now in world " + worldId);
        if (stale > 0) {
            log.warn("{} commands for the previous world will never be acknowledged; marked FAILED", stale);
        }
        WareHouse warehouse = warehouses.findById((long) WorldGateway.WAREHOUSE_ID).orElseGet(() -> {
            WareHouse created = new WareHouse();
            created.setName("Warehouse " + WorldGateway.WAREHOUSE_ID);
            created.setXCoordinate(10);
            created.setYCoordinate(20);
            return warehouses.save(created);
        });

        for (Product product : products.findAll()) {
            int onHand = products.takeAllStock(product.getProductId());
            reorder(product.getProductId(), product.getName(), onHand);
        }
        seedCatalog(warehouse.getId());
        log.info("Adopted world {}: stock is on order and becomes available as it arrives", worldId);
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
            replenishment.purchase(WorldGateway.WAREHOUSE_ID, Long.parseLong(productId), name, count);
        } catch (NumberFormatException e) {
            log.warn("Product {} has a non-numeric id and cannot be ordered from the world", productId);
        }
    }
}
