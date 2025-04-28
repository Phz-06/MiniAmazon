package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.dto.response.PackageDTO;
import edu.ece568.MiniAmazon.model.AmazonPackage;
import edu.ece568.MiniAmazon.repository.PackageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/packages")
@Slf4j
public class PackageController {

    private final PackageRepository packageRepository;

    public PackageController(PackageRepository packageRepository) {
        this.packageRepository = packageRepository;
    }

    @PreAuthorize("@orderSecurity.isOrderOwner(#orderId)")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PackageDTO> getPackageByOrderId(@PathVariable Long orderId) {
        log.info("Fetching package for orderId: {}", orderId);

        AmazonPackage amazonPackage = packageRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Package not found for order ID: " + orderId));

        return ResponseEntity.ok(PackageDTO.fromEntity(amazonPackage));
    }
}
