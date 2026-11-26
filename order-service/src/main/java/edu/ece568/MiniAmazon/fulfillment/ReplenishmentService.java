package edu.ece568.MiniAmazon.fulfillment;

import edu.ece568.MiniAmazon.proto.Events.Item;
import edu.ece568.MiniAmazon.proto.Events.PurchaseStock;
import edu.ece568.MiniAmazon.proto.Events.StockArrived;
import edu.ece568.MiniAmazon.service.ProductService;
import edu.ece568.MiniAmazon.shared.messaging.EventOutbox;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock enters the system in two steps: we order it from the warehouse, and it
 * becomes sellable only when the warehouse says it arrived. Until then it is
 * not in the product's quantity, so nobody can buy what the warehouse does not hold.
 */
@Slf4j
@Service
public class ReplenishmentService {

    private final EventOutbox outbox;
    private final ProductService productService;

    public ReplenishmentService(EventOutbox outbox, ProductService productService) {
        this.outbox = outbox;
        this.productService = productService;
    }

    /** Orders stock. Returns the id of the outbox row carrying the request. */
    @Transactional
    public long purchase(int warehouseId, long productId, String description, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Purchase quantity must be positive");
        }
        PurchaseStock purchase = PurchaseStock.newBuilder().setWarehouseId(warehouseId)
                .addItems(Item.newBuilder().setProductId(productId).setDescription(description).setCount(count))
                .build();
        return outbox.publish(Topics.WAREHOUSE_COMMANDS, "product-" + productId, e -> e.setPurchaseStock(purchase));
    }

    /** The warehouse received goods. Runs inside the consumer's transaction, so a redelivery cannot add them twice. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void received(StockArrived arrived) {
        for (Item item : arrived.getItemsList()) {
            productService.increaseInventory(item.getProductId(), item.getDescription(), item.getCount(), arrived.getWarehouseId());
            log.info("Received {} x product {} at warehouse {}", item.getCount(), item.getProductId(), arrived.getWarehouseId());
        }
    }
}
