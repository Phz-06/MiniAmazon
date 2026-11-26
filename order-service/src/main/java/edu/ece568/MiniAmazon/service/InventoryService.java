package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.exception.InsufficientStockException;
import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Moves stock for an order. Every change is a single atomic UPDATE on the
 * product row; nothing here reads a quantity and writes it back.
 *
 * Each UPDATE takes that product's row lock and keeps it until the surrounding
 * transaction ends. Two orders holding {A, B} and {B, A} would therefore wait
 * on each other forever, and Postgres would kill one after deadlock_timeout.
 * Taking the locks in one global order (product id, ascending) makes that
 * cycle impossible, so lines are sorted before any row is touched.
 */
@Service
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Must run inside the caller's transaction: a partial reservation has to roll back with it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockInventory(List<OrderItem> items) {
        for (Map.Entry<String, Integer> line : inLockOrder(items).entrySet()) {
            if (productRepository.decreaseProductQuantity(line.getKey(), line.getValue()) == 0) {
                throw new InsufficientStockException(line.getKey());
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void unlockInventory(List<OrderItem> items) {
        for (Map.Entry<String, Integer> line : inLockOrder(items).entrySet()) {
            if (productRepository.increaseProductQuantity(line.getKey(), line.getValue()) == 0) {
                throw new IllegalStateException("Product " + line.getKey() + " no longer exists");
            }
        }
    }

    /** Total quantity per product, iterated in ascending product id. */
    private static Map<String, Integer> inLockOrder(List<OrderItem> items) {
        Map<String, Integer> byProduct = new TreeMap<>();
        for (OrderItem item : items) {
            byProduct.merge(item.getProduct().getProductId(), item.getQuantity(), Integer::sum);
        }
        return byProduct;
    }
}
