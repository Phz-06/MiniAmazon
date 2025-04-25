package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends CrudRepository<Order, Long> {
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId")
    List<Order> findOrdersById(@Param("userId") Long userId);

    Optional<Order> findOrderByShipmentId(String shipmentId);
}

