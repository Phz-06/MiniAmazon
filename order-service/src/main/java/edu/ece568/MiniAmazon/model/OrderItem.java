package edu.ece568.MiniAmazon.model;

import java.math.BigDecimal;
import jakarta.persistence.Column;
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
    /** Unit price at the moment the order was placed; later catalog edits must not change it. */
    @Column(nullable = false)
    private BigDecimal price;

    @ManyToOne
    private Order order;
    @ManyToOne
    private Product product;

}
