package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.AmazonPackage;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PackageRepository extends CrudRepository<AmazonPackage, Long> {
    @Query("SELECT p FROM AmazonPackage p WHERE p.order.id = :orderId")
    Optional<AmazonPackage> findByOrderId(@Param("orderId") Long orderId);
}
