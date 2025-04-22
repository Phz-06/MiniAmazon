package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.Product;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {
    Product findByName(String name);
    Product findByProductId(String productID);
    Product findByWareHouse_Name(String wareHouseName);
    Product findByWareHouse_xCoordinateAndWareHouse_yCoordinate(int x, int y);
    int findQuantityByProductId(String productID);

}
