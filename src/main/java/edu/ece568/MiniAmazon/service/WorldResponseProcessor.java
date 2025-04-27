package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.Event.LoadConfirmationEvent;
import edu.ece568.MiniAmazon.Event.ShipmentRequestEvent;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.Status;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.data.repository.util.ClassUtils.ifPresent;

/**
 * Service to process responses from the World Simulator
 */
@Service
@Slf4j
public class WorldResponseProcessor {

    private final WorldService worldService;
    private final ProductService productService;
    private final EventPublisher eventPublisher;
    
    // Map to store package details (destination coordinates, UPS account) keyed by shipment ID
    private final Map<Long, Map<String, Object>> packageDetailsMap = new HashMap<>();
    private final OrderService orderService;

    @Autowired
    public WorldResponseProcessor(WorldService worldService, ProductService productService, EventPublisher eventPublisher, OrderService orderService) {
        this.worldService = worldService;
        this.productService = productService;
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
    }

    /**
     * Register a package's details for future reference
     */
    public void registerPackageDetails(String packageId, int warehouseId, int destX, int destY, String upsAccount) {
        // Convert package ID string to numeric shipment ID
        long shipmentId = Long.parseLong(packageId.replace("PKG-", ""));
        
        Map<String, Object> details = new HashMap<>();
        details.put("packageId", packageId);
        details.put("warehouseId", warehouseId);
        details.put("destX", destX);
        details.put("destY", destY);
        details.put("upsAccount", upsAccount);
        
        packageDetailsMap.put(shipmentId, details);
        log.info("Registered package details for shipment ID: {}, package ID: {}", shipmentId, packageId);
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
    @Transactional
    protected void processReadyPackage(APacked ready) {
        long shipmentId = ready.getShipid();
        long seqNum = ready.getSeqnum();
        
        log.info("Package {} is packed and ready for pickup (seqNum: {})", shipmentId, seqNum);
        
        try {
            // Get the package details
            Map<String, Object> packageDetails = packageDetailsMap.get(shipmentId);
            if (packageDetails == null) {
                log.error("No package details found for shipment ID: {}", shipmentId);
                return;
            }
            
            // Get order and update status in a separate transaction
            Order order = orderService.getOrderByShipmentId("PKG-" + shipmentId);
            if (order == null) {
                log.error("No order found for shipment ID: {}", shipmentId);
                return;
            } 

            
            // Fetch product information for description - use the first item as representative

            String description = order.getOrderItems().get(0).getProduct().getDescription();

            log.warn("Could not get product description for shipment {}, using empty string", shipmentId);
            log.info("the description is {}", description);
            order.setStatus(Status.PACKED);
            orderService.saveOrder(order);

            String packageId = (String) packageDetails.get("packageId");
            int warehouseId = (int) packageDetails.get("warehouseId");
            int destX = (int) packageDetails.get("destX");
            int destY = (int) packageDetails.get("destY");
            String upsAccount = (String) packageDetails.get("upsAccount");
            
            // Publish event to request UPS to pick up the package
            ShipmentRequestEvent shipmentRequestEvent = new ShipmentRequestEvent(
                    packageId + "_" + description,
                    warehouseId,
                    destX,
                    destY,
                    upsAccount
            );
            
            eventPublisher.publish(shipmentRequestEvent);
            log.info("Published shipment request event for package: {}", packageId);
            
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
            // Get package details
            Map<String, Object> packageDetails = packageDetailsMap.get(shipmentId);
            if (packageDetails == null) {
                log.error("No package details found for shipment ID: {}", shipmentId);
                return;
            }
            
            String packageId = (String) packageDetails.get("packageId");
            
            // Update shipment status in the database
            log.info("Updating shipment {} status to LOADED", shipmentId);

            Integer truckId = (Integer) packageDetails.get("truckId");
            Order order = orderService.getOrderByShipmentId("PKG-" + shipmentId);
            if (order != null) {
                order.setStatus(Status.LOADED);
                orderService.saveOrder(order);
            }
            if (truckId == null) {
                log.error("No truck ID associated with package: {}", packageId);
                return;
            }
            
            // Publish event to confirm loading to UPS
            LoadConfirmationEvent loadConfirmationEvent = new LoadConfirmationEvent(
                    packageId,
                    truckId
            );
            
            eventPublisher.publish(loadConfirmationEvent);
            log.info("Published load confirmation event for package: {}, truck: {}", packageId, truckId);
            
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
            // Record the package status in the database (you'll need to implement this)
            log.info("Updating package {} with status: {}", packageId, status);
            
            // Depending on the status, you might want to trigger different actions
            // For now, we'll just log the status
        } catch (Exception e) {
            log.error("Error processing package status for package {}", packageId, e);
        }
    }

    /**
     * Process error messages from the World Simulator
     * 
     * @param error The AErr message
     */
    private void processError(AErr error) {
        String errorMessage = error.getErr();
        long originSeqNum = error.getOriginseqnum();
        long seqNum = error.getSeqnum();
        
        log.error("Error from World: {} (originSeqNum: {}, seqNum: {})", errorMessage, originSeqNum, seqNum);
    }
    
    /**
     * Store the truck ID for a package when we receive a truck assignment
     */
    public void associateTruckWithPackage(String packageId, int truckId) {
        try {
            // Convert package ID to shipment ID
            long shipmentId = Long.parseLong(packageId.replace("PKG-", ""));
            
            // Update the package details
            Map<String, Object> packageDetails = packageDetailsMap.get(shipmentId);
            if (packageDetails != null) {
                packageDetails.put("truckId", truckId);
                log.info("Associated truck {} with package {}", truckId, packageId);
            } else {
                log.error("No package details found for package ID: {}", packageId);
            }
        } catch (Exception e) {
            log.error("Error associating truck with package: {}", packageId, e);
        }
    }
} 