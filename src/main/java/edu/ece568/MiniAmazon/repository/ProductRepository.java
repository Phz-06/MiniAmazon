package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {
    Product findByName(String name);
    Product findByProductId(String productID);
    Product findByWareHouse_Name(String wareHouseName);
    Product findByWareHouse_xCoordinateAndWareHouse_yCoordinate(int x, int y);
    int findQuantityByProductId(String productID);

    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :quantity WHERE p.productId = :productId AND p.quantity >= :quantity")
    int decreaseProductQuantity(@Param("productId") String productId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity + :quantity WHERE p.productId = :productId")
    int increaseProductQuantity(@Param("productId") String productId, @Param("quantity") int quantity);
}
