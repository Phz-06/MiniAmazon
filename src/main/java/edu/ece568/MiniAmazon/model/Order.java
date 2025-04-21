package edu.ece568.MiniAmazon.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "customer_order")
public class Order extends AbstractPersistable<Long> {

    @OneToMany
    private List<OrderItem> orderItems;

    private int deliveryX;  // delivery x-coordinate
    private int deliveryY;  // delivery y-coordinate

    @Enumerated(EnumType.STRING)
    private Status status;  // e.g., CREATED, PACKING, PACKED, LOADING, LOADED, DELIVERING, DELIVERED

    private String upsAccountName;  // optional
}