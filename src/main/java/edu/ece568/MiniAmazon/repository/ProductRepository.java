package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.Product;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends CrudRepository<Product, Long> {
    Product findByName(String name);
    Product findByProductID(String productID);
    Product findByWareHouse_Name(String wareHouseName);
    Product findByWareHouse_XCoordinateAndWareHouse_YCoordinate(int x, int y);
    int findQuantityByProductID(String productID);

}
