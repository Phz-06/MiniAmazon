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
        long seqNum = ready.getSeqnum();
        
        log.info("Package {} is packed and ready for pickup (seqNum: {})", shipmentId, seqNum);
        
        try {
            // Update shipment status in the database
            // You'll need to create or inject a ShipmentService to handle these operations
            // Example:
            // shipmentService.updateStatus(shipmentId, ShipmentStatus.PACKED);
            
            // For now, we'll just log it since the service isn't implemented
            log.info("Updating shipment {} status to PACKED", shipmentId);
            
            // Here you would also:
            // 1. Update any tracking information
            // 2. Notify other components that might need to take action
            // 3. Prepare for the next step in the shipping process
            
            // You could also create a task for the next step (e.g., loading onto truck)
            // Example:
            // taskService.createLoadTask(shipmentId, warehouse);
        } catch (Exception e) {
            log.error("Error processing ready package notification for shipment {}", shipmentId, e);
        }
    }

    /**
     * Process loaded package notification
     * 
     * @param loaded The ALoaded message
     */
    private void processLoadedPackage(ALoaded loaded) {
        long shipmentId = loaded.getShipid();
        long seqNum = loaded.getSeqnum();
        
        log.info("Package {} is loaded onto truck (seqNum: {})", shipmentId, seqNum);
        
        try {
            // Update shipment status in the database
            // Example:
            // shipmentService.updateStatus(shipmentId, ShipmentStatus.LOADED);
            
            // For now, just log it
            log.info("Updating shipment {} status to LOADED", shipmentId);
            
            // Here you would:
            // 1. Update tracking information
            // 2. Notify relevant systems (e.g., delivery tracking)
            // 3. Update inventory and warehouse management systems
            
            // You could also trigger the next step in the delivery process
            // Example:
            // deliveryService.initiateDelivery(shipmentId);
        } catch (Exception e) {
            log.error("Error processing loaded package notification for shipment {}", shipmentId, e);
        }
    }

    /**
     * Process package status response
     * 
     * @param packageStatus The APackage message
     */
    private void processPackageStatus(APackage packageStatus) {
        long packageId = packageStatus.getPackageid();
        String status = packageStatus.getStatus();
        long seqNum = packageStatus.getSeqnum();
        
        log.info("Package {} has status: {} (seqNum: {})", packageId, status, seqNum);

        try {
            // Record the package status in the database
            // Example:
            // shipmentService.updateShipmentStatus(packageId, status);
            
            // Log the status update
            log.info("Updating package {} with status: {}", packageId, status);
            
            // Depending on the status, you might want to trigger different actions
            // For example, if the status is "delivered", you might want to:
            // 1. Update the order status
            // 2. Send a notification to the customer
            // 3. Update inventory records
            
            // Example:
            // if (status.equals("delivered")) {
            //     orderService.completeOrder(packageId);
            //     notificationService.notifyCustomer(packageId, "Your package has been delivered!");
            // }
        } catch (Exception e) {
            log.error("Error processing package status for package {}", packageId, e);
        }
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