package edu.ece568.MiniAmazon.Listener;

import edu.ece568.MiniAmazon.Event.LoadConfirmationEvent;
import edu.ece568.MiniAmazon.Event.LoadPackageEvent;
import edu.ece568.MiniAmazon.Event.PackOrderEvent;
import edu.ece568.MiniAmazon.Event.ShipmentRequestEvent;
import edu.ece568.MiniAmazon.service.EventPublisher;
import edu.ece568.MiniAmazon.service.WorldResponseProcessor;
import edu.ece568.MiniAmazon.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WorldEventListener {

    private final WorldService worldService;
    private final EventPublisher eventPublisher;
    private final WorldResponseProcessor worldResponseProcessor;

    public WorldEventListener(WorldService worldService, EventPublisher eventPublisher, 
                              WorldResponseProcessor worldResponseProcessor) {
        this.worldService = worldService;
        this.eventPublisher = eventPublisher;
        this.worldResponseProcessor = worldResponseProcessor;
    }

    /**
     * Handle the PackOrderEvent to initiate packing an order
     */
    @EventListener
    public void handlePackOrderEvent(PackOrderEvent event) {
        log.info("Handling pack order event for order id: {}, package id: {}", event.getOrderId(), event.getPackageId());
        
        try {
            // Convert the items to the format expected by the world service
            List<Object[]> products = new ArrayList<>();
            for (Map<String, Object> item : event.getItems()) {
                Object[] product = new Object[3];
                product[0] = item.get("product_id");  // product ID
                product[1] = item.get("description"); // description
                product[2] = item.get("quantity");    // quantity
                products.add(product);
            }
            
            // Register package details with the WorldResponseProcessor
            worldResponseProcessor.registerPackageDetails(
                    event.getPackageId(),
                    event.getWarehouseId(),
                    event.getDestX(),
                    event.getDestY(),
                    event.getUpsAccount()
            );
            
            // Request the world service to pack the order
            long seqNum = worldService.packOrder(
                    event.getWarehouseId(),
                    Long.parseLong(event.getPackageId()), // Convert to a numeric shipment ID
                    products
            );
            
            log.info("Sent pack request to world service for package: {}, seqNum: {}", 
                    event.getPackageId(), seqNum);
            
            // The WorldResponseProcessor will handle the response and trigger the next step
        } catch (Exception e) {
            log.error("Error packing order for order id: {}, package id: {}", 
                    event.getOrderId(), event.getPackageId(), e);
        }
    }

    /**
     * Handle the LoadPackageEvent to load a package onto a truck
     */
    @EventListener
    public void handleLoadPackageEvent(LoadPackageEvent event) {
        log.info("Handling load package event for package id: {}, truck id: {}", 
                event.getPackageId(), event.getTruckId());
        
        try {
            // Parse the package ID to get the numeric shipment ID
            long shipmentId = Long.parseLong(event.getPackageId());
            
            // Request the world service to load the package onto the truck
            long seqNum = worldService.loadPackage(
                    1, // Using default warehouse ID for now
                    event.getTruckId(),
                    shipmentId
            );
            
            log.info("Sent load request to world service for package: {}, truck: {}, seqNum: {}", 
                    event.getPackageId(), event.getTruckId(), seqNum);
        } catch (Exception e) {
            log.error("Error loading package for package id: {}, truck id: {}", 
                    event.getPackageId(), event.getTruckId(), e);
        }
    }
} 