package edu.ece568.MiniAmazon.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import org.springframework.data.jpa.domain.AbstractPersistable;


/**
 * OrderItem represents a specific item in an order, with its quantity and price.
 */
@Entity
public class OrderItem extends AbstractPersistable<Long> {
    private int quantity;
    private double price;

    @ManyToOne
    private Order order;
    @ManyToOne
    private Product product;

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}
