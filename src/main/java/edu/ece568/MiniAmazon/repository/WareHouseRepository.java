package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.WareHouse;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface WareHouseRepository extends CrudRepository<WareHouse, Long> {
}
