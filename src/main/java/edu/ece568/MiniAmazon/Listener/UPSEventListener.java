package edu.ece568.MiniAmazon.Listener;

import edu.ece568.MiniAmazon.Event.LoadConfirmationEvent;
import edu.ece568.MiniAmazon.Event.PackageStatusUpdateEvent;
import edu.ece568.MiniAmazon.Event.ShipmentRequestEvent;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.UPSService;
import edu.ece568.MiniAmazon.service.UPSSocketServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UPSEventListener {

    private final UPSService upsService;
    private final OrderService orderService;
    private final UPSSocketServerService uPSSocketServerService;

    public UPSEventListener(UPSService upsService, OrderService orderService, UPSSocketServerService uPSSocketServerService) {
        this.upsService = upsService;
        this.orderService = orderService;
        this.uPSSocketServerService = uPSSocketServerService;
    }

    /**
     * Handle ShipmentRequestEvent to request UPS to pick up a package
     */
    @EventListener
    public void handleShipmentRequestEvent(ShipmentRequestEvent event) {
        log.info("Handling shipment request event for package id: {}", event.getPackageId());



        try {
            // Send the shipment request to UPS
            long seqNum = uPSSocketServerService.sendShipmentRequest(
                    event.getPackageId(),
                    event.getWarehouseId(),
                    event.getDestX(),
                    event.getDestY(),
                    event.getUpsAccount()
            );
            
            if (seqNum > 0) {
                log.info("Sent shipment request to UPS for package: {}, seqNum: {}, ",
                        event.getPackageId(), seqNum);
            } else {
                log.error("Failed to send shipment request to UPS for package: {}", 
                        event.getPackageId());
            }
        } catch (Exception e) {
            log.error("Error sending shipment request to UPS for package id: {}", 
                    event.getPackageId(), e);
        }
    }

    /**
     * Handle LoadConfirmationEvent to confirm to UPS that a package is loaded
     */
    @EventListener
    public void handleLoadConfirmationEvent(LoadConfirmationEvent event) {
        log.info("Handling load confirmation event for package id: {}, truck id: {}", 
                event.getPackageId(), event.getTruckId());
        
        try {
            // Send the load confirmation to UPS
            long seqNum = uPSSocketServerService.sendLoadConfirmation(
                    event.getPackageId(),
                    event.getTruckId()
            );
            
            if (seqNum > 0) {
                log.info("Sent load confirmation to UPS for package: {}, truck: {}, seqNum: {}", 
                        event.getPackageId(), event.getTruckId(), seqNum);
            } else {
                log.error("Failed to send load confirmation to UPS for package: {}, truck: {}", 
                        event.getPackageId(), event.getTruckId());
            }
        } catch (Exception e) {
            log.error("Error sending load confirmation to UPS for package id: {}, truck id: {}", 
                    event.getPackageId(), event.getTruckId(), e);
        }
    }

    @EventListener
    public void handleDeliveredEvent(PackageStatusUpdateEvent event) {
        log.info("Handling delivered event for package id: {}", event.getPackageId());
        orderService.setStatus(event.getPackageId(), Status.DELIVERED);
    }
} 