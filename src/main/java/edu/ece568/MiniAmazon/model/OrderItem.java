package edu.ece568.MiniAmazon.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;


/**
 * OrderItem represents a specific item in an order, with its quantity and price.
 */
@Setter
@Getter
@Entity
public class OrderItem extends AbstractPersistable<Long> {
    private int quantity;
    private double price;

    @ManyToOne
    private Order order;
    @ManyToOne
    private Product product;

}
