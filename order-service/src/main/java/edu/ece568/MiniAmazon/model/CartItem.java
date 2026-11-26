package edu.ece568.MiniAmazon.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.math.BigDecimal;

@Setter
@Getter
@Entity
public class CartItem extends AbstractPersistable<Long> {
    private int quantity;
    private BigDecimal price;

    @ManyToOne
    private ShoppingCart cart;
    
    @ManyToOne
    private Product product;
} 