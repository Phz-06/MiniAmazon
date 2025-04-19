package edu.ece568.MiniAmazon.model;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.util.List;

@Entity
public class Order extends AbstractPersistable<Long> {

    @OneToMany
    private List<OrderItem> orderItems;

    private int deliveryX;  // delivery x-coordinate
    private int deliveryY;  // delivery y-coordinate

    @Enumerated(EnumType.STRING)
    private Status status;  // e.g., CREATED, PACKING, PACKED, LOADING, LOADED, DELIVERING, DELIVERED

    private String upsAccountName;  // optional

    public List<OrderItem> orderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public int getDeliveryX() {
        return deliveryX;
    }

    public void setDeliveryX(int deliveryX) {
        this.deliveryX = deliveryX;
    }

    public int getDeliveryY() {
        return deliveryY;
    }

    public void setDeliveryY(int deliveryY) {
        this.deliveryY = deliveryY;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getUpsAccountName() {
        return upsAccountName;
    }

    public void setUpsAccountName(String upsAccountName) {
        this.upsAccountName = upsAccountName;
    }

}