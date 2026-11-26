package edu.ece568.MiniAmazon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.math.BigDecimal;

@Setter
@Getter
@Entity
public class Product extends AbstractPersistable<Long> {

    private String name;

    @Column(unique = true)
    private String productId;
    private BigDecimal price;
    private String description;
    /**
     * Stock on hand. Deliberately not updatable through the entity: Hibernate
     * writes every column on save, so an admin editing the price would write
     * back a stale quantity and erase concurrent purchases. Stock only moves
     * through the atomic statements in ProductRepository.
     */
    @Column(nullable = false, updatable = false)
    private int quantity;
    private String category;

    @ManyToOne
    private WareHouse wareHouse;

}
