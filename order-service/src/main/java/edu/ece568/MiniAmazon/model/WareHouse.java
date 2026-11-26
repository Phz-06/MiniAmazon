package edu.ece568.MiniAmazon.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "ware_house")
public class WareHouse extends AbstractPersistable<Long> {
    private String name;

    @OneToMany(mappedBy = "wareHouse")
    private List<Product> products;

    private int xCoordinate;
    private int yCoordinate;

}
