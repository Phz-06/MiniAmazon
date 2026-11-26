package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Status;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends CrudRepository<Order, Long> {

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId")
    List<Order> findOrdersById(@Param("userId") Long userId);

    long countByStatusIn(java.util.Collection<Status> statuses);

    /** Ownership check as one index lookup, without loading the order and its items. */
    boolean existsByIdAndUser_Username(Long id, String username);

    /**
     * Compare-and-set on the order status. Returns 1 for the single caller that
     * moved the order out of {@code from}, 0 for everyone else.
     *
     * Reading the status and then saving the entity lets two requests both see
     * CONFIRMED and both act on it (pay twice, or pay and cancel). Here the
     * check and the write are one statement, so the row lock decides the winner.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :to WHERE o.id = :id AND o.status = :from")
    int transition(@Param("id") Long id, @Param("from") Status from, @Param("to") Status to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Order o SET o.truckId = :truckId WHERE o.id = :id")
    int assignTruck(@Param("id") Long id, @Param("truckId") int truckId);
}
