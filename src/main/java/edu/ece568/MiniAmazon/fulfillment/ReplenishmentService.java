package edu.ece568.MiniAmazon.fulfillment;

import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.service.ProductService;
import edu.ece568.MiniAmazon.world.WorldCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stock enters the system in two steps: we order it from the world, and it
 * becomes sellable only when the world says it arrived. Until then it is not
 * in the product's quantity, so nobody can buy what the warehouse does not hold.
 */
@Slf4j
@Service
public class ReplenishmentService {

    private final Outbox outbox;
    private final ProductService productService;

    public ReplenishmentService(Outbox outbox, ProductService productService) {
        this.outbox = outbox;
        this.productService = productService;
    }

    /** Orders stock from the world. Returns the command's sequence number. */
    @Transactional
    public long purchase(int warehouseId, long productId, String description, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Purchase quantity must be positive");
        }
        List<AProduct> things = List.of(WorldCommands.product(productId, description, count));
        return outbox.enqueue(Destination.WORLD, WorldCommands.PURCHASE, null,
                seq -> WorldCommands.purchase(seq, warehouseId, things));
    }

    /** The world delivered. Runs inside the inbox transaction, so a redelivery cannot add the stock twice. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void received(APurchaseMore arrived) {
        for (AProduct thing : arrived.getThingsList()) {
            productService.increaseInventory(thing.getId(), thing.getDescription(), thing.getCount(), arrived.getWhnum());
            log.info("Received {} x product {} at warehouse {}", thing.getCount(), thing.getId(), arrived.getWhnum());
        }
    }
}
