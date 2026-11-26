package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.ShoppingCart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShoppingCartRepository extends CrudRepository<ShoppingCart, Long> {
    /** Cart with its lines and their products in one query: the DTO is built after the transaction ends. */
    @EntityGraph(attributePaths = {"cartItems", "cartItems.product"})
    Optional<ShoppingCart> findByUser_Id(Long userId);
} 