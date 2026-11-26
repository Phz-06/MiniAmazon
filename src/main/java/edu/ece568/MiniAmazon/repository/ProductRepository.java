package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {
    Product findByName(String name);
    Product findByProductId(String productID);
    Product findByWareHouse_Name(String wareHouseName);
    Product findByWareHouse_xCoordinateAndWareHouse_yCoordinate(int x, int y);
    int findQuantityByProductId(String productID);

    /**
     * Reserve stock. The availability check and the decrement are one statement,
     * so two buyers cannot both pass a check against the same last unit.
     * Returns 0 when there is not enough left.
     */
    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :quantity WHERE p.productId = :productId AND p.quantity >= :quantity")
    int decreaseProductQuantity(@Param("productId") String productId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity + :quantity WHERE p.productId = :productId")
    int increaseProductQuantity(@Param("productId") String productId, @Param("quantity") int quantity);

    /** Absolute stock correction (admin). Still a single statement, never a read-modify-write. */
    @Modifying
    @Query("UPDATE Product p SET p.quantity = :quantity WHERE p.productId = :productId")
    int setProductQuantity(@Param("productId") String productId, @Param("quantity") int quantity);
    
    // Find products by category
    List<Product> findByCategory(String category);
    
    // Find all products with quantity greater than zero
    @Query("SELECT p FROM Product p WHERE p.quantity > 0")
    List<Product> findAvailableProducts();
    
    // Get all distinct product categories
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.quantity > 0")
    List<String> findAllCategories();
}
