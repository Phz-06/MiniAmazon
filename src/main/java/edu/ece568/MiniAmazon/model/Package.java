package edu.ece568.MiniAmazon.model;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.AbstractPersistable;

@Entity
public class Package extends AbstractPersistable<Long> {
    @Column(unique = true)
    private String trackingNumber;

    @ManyToOne
    private Order order;
    @Enumerated(EnumType.STRING)
    private Status status;

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
