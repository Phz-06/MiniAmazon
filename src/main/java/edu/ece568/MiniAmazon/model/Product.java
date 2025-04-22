package edu.ece568.MiniAmazon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;

@Setter
@Getter
@Entity
public class Product extends AbstractPersistable<Long> {

    private String name;

    @Column(unique = true)
    private String productId;
    private double price;
    private String description;
    private int quantity;
    private String category;

    @ManyToOne
    private WareHouse wareHouse;

}
