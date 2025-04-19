package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.Order;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends CrudRepository<Order, Long> {
}
