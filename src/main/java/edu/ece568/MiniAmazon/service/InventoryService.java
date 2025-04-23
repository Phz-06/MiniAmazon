package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public boolean isAvailable(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getQuantity() > item.getProduct().getQuantity()) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public void lockInventory(List<OrderItem> items) {
        for (OrderItem item : items) {
            int updatedRows = productRepository.decreaseProductQuantity(item.getProduct().getProductId(), item.getQuantity());
            if (updatedRows == 0) {
                throw new RuntimeException("Not enough inventory for product ID: " + item.getProduct().getProductId());
            }
        }
    }

    @Transactional
    public void unlockInventory(List<OrderItem> items) {
        for (OrderItem item : items) {
            int updatedRows = productRepository.increaseProductQuantity(item.getProduct().getProductId(), item.getQuantity());
            if (updatedRows == 0) {
                throw new RuntimeException("Failed to unlock inventory for product ID: " + item.getProduct().getProductId());
            }
        }
    }
}
