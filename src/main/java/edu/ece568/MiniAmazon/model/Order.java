package edu.ece568.MiniAmazon.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "customer_order")
public class Order extends AbstractPersistable<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private AmazonPackage amazonPackage;

    @Enumerated(EnumType.STRING)
    private Status status;  // e.g., CREATED, PACKING, PACKED, LOADING, LOADED, DELIVERING, DELIVERED

    private String upsAccountName;  // optional

    private BigDecimal totalPrice;  // total price of the order

    private int deliveryX;
    private int deliveryY;
}