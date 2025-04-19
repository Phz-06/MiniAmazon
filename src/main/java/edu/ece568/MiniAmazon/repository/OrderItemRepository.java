package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.OrderItem;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends CrudRepository<OrderItem, Long> {

}
