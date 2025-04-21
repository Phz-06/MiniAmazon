package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.AmazonPackage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackageRepository extends CrudRepository<AmazonPackage, Long> {
}
