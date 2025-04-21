package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APacked;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ALoaded;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APackage;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AErr;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service to process responses from the World Simulator
 */
@Service
@Slf4j
public class WorldResponseProcessor {

    private final WorldService worldService;
    private final ProductService productService;

    @Autowired
    public WorldResponseProcessor(WorldService worldService, ProductService productService) {
        this.worldService = worldService;
        this.productService = productService;
    }

    /**
     * Process responses from the World Simulator.
     * This method is scheduled to run every 100ms.
     */
    @Scheduled(fixedDelay = 100)
    public void processResponses() {
        if (!worldService.isConnected()) {
            return;
        }

        AResponses responses = worldService.getNextResponse();
        if (responses == null) {
            return;
        }

        // Process arrived products (items that were purchased have arrived at warehouse)
        for (APurchaseMore arrived : responses.getArrivedList()) {
            processArrivedProducts(arrived);
        }

        // Process ready notifications (items are packed and ready for pickup)
        for (APacked ready : responses.getReadyList()) {
            processReadyPackage(ready);
        }

        // Process loaded notifications (items are loaded onto truck)
        for (ALoaded loaded : responses.getLoadedList()) {
            processLoadedPackage(loaded);
        }

        // Process package status responses (status of a package that was queried)
        for (APackage packageStatus : responses.getPackagestatusList()) {
            processPackageStatus(packageStatus);
        }

        // Process errors
        for (AErr error : responses.getErrorList()) {
            processError(error);
        }

        // Check if world is finished
        if (responses.hasFinished() && responses.getFinished()) {
            log.info("World has finished");
        }
    }

    /**
     * Process arrived products
     * 
     * @param arrived The APurchaseMore message
     */
    private void processArrivedProducts(APurchaseMore arrived) {
        int warehouseId = arrived.getWhnum();
        log.info("Products arrived at warehouse {}", warehouseId);

        // Process each product
        for (AProduct product : arrived.getThingsList()) {
            long productId = product.getId();
            String description = product.getDescription();
            int count = product.getCount();

            log.info("Product {} ({}) arrived, quantity: {}", productId, description, count);
            // Update inventory in the database
            try {
                productService.increaseInventory(productId, description, count, warehouseId);
            } catch (Exception e) {
                log.error("Error updating inventory for product {}", productId, e);
            }
        }
    }

    /**
     * Process ready package notification
     * 
     * @param ready The APacked message
     */
    private void processReadyPackage(APacked ready) {
        long shipmentId = ready.getShipid();
        log.info("Package {} is packed and ready for pickup", shipmentId);
        //TODO: Update package status in the database
    }

    /**
     * Process loaded package notification
     * 
     * @param loaded The ALoaded message
     */
    private void processLoadedPackage(ALoaded loaded) {
        long shipmentId = loaded.getShipid();
        log.info("Package {} is loaded onto truck", shipmentId);
        //TODO: Update package status in the database
    }

    /**
     * Process package status response
     * 
     * @param packageStatus The APackage message
     */
    private void processPackageStatus(APackage packageStatus) {
        long packageId = packageStatus.getPackageid();
        String status = packageStatus.getStatus();
        log.info("Package {} has status: {}", packageId, status);

        //TODO: Update package status in the database
    }

    /**
     * Process error response
     * 
     * @param error The AErr message
     */
    private void processError(AErr error) {
        String errorMessage = error.getErr();
        long originSeqNum = error.getOriginseqnum();
        log.error("Error from world for command {}: {}", originSeqNum, errorMessage);

        //TODO: Handle error in the application
    }
} 