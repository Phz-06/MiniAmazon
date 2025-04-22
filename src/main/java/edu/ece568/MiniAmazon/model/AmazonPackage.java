package edu.ece568.MiniAmazon.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;

@Setter
@Getter
@Entity
public class AmazonPackage extends AbstractPersistable<Long> {
    @Column(unique = true)
    private String shipmentID;

    @Enumerated(EnumType.STRING)
    private Status status;

    @OneToOne
    private Order order;


}
