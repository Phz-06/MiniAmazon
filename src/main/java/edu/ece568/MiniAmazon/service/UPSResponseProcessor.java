package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.Event.LoadPackageEvent;
import edu.ece568.MiniAmazon.Event.PackageStatusUpdateEvent;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.GenericMessage;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.TruckAssignment;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.DeliveryUpdate;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.DeliveryCompleted;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.Error;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service to process responses from UPS
 */
@Service
@Slf4j
public class UPSResponseProcessor {

    private final UPSService upsService;
    private final EventPublisher eventPublisher;
    private final WorldService worldService;
    private final OrderService orderService;

    @Autowired
    public UPSResponseProcessor(UPSService upsService, EventPublisher eventPublisher, WorldService worldService, OrderService orderService) {
        this.upsService = upsService;
        this.eventPublisher = eventPublisher;
        this.worldService = worldService;
        this.orderService = orderService;
    }

    /**
     * Process responses from UPS.
     * This method is scheduled to run every 100ms.
     */
    @Scheduled(fixedDelay = 100)
    public void processResponses() {
        if (!upsService.isConnected()) {
            return;
        }

        GenericMessage message = upsService.getNextMessage();
        if (message == null) {
            return;
        }

        // Process message based on type
        switch (message.getType()) {
            case "truck_assignment":
                processTruckAssignment(message.getTruckAssignment());
                break;
            case "delivery_update":
                processDeliveryUpdate(message.getDeliveryUpdate());
                break;
            case "delivery_completed":
                processDeliveryCompleted(message.getDeliveryCompleted());
                break;
            case "error":
                processError(message.getError());
                break;
            default:
                log.warn("Received unknown message type from UPS: {}", message.getType());
                break;
        }
    }

    /**
     * Process truck assignment notifications
     * 
     * @param truckAssignment The truck assignment message
     */
    private void processTruckAssignment(TruckAssignment truckAssignment) {
        String packageId = truckAssignment.getPackageId();
        int truckId = truckAssignment.getTruckId();
        Order order = orderService.getOrderByShipmentId("PKG-" + packageId);
        if (order != null) {
            order.setTruckId(truckId);
            order.setStatus(Status.LOADING);
            orderService.saveOrder(order);
        }
        log.info("Truck assignment received for package: {}, truck: {}", packageId, truckId);
        
        try {
            // Publish event to load the package onto the truck
            LoadPackageEvent loadPackageEvent = new LoadPackageEvent(packageId, truckId);
            eventPublisher.publish(loadPackageEvent);
            
            log.info("Published load package event for package: {}, truck: {}", packageId, truckId);
        } catch (Exception e) {
            log.error("Error processing truck assignment for package: {}", packageId, e);
        }
    }

    /**
     * Process delivery update notifications
     * 
     * @param deliveryUpdate The delivery update message
     */
    private void processDeliveryUpdate(DeliveryUpdate deliveryUpdate) {
        String packageId = deliveryUpdate.getPackageId();
        DeliveryUpdate.Status status = deliveryUpdate.getStatus();
        
        log.info("Delivery update received for package: {}, status: {}", packageId, status);
        
        try {
            // Publish event to update package status
            String statusStr = "";
            switch (status) {
                case PACKING:
                    statusStr = "PACKING";
                    break;
                case OUT_FOR_DELIVERY:
                    statusStr = "OUT_FOR_DELIVERY";
                    break;
                case DELIVERING:
                    statusStr = "DELIVERING";
                    break;
            }
            
            PackageStatusUpdateEvent statusEvent = new PackageStatusUpdateEvent(packageId, statusStr);
            eventPublisher.publish(statusEvent);
            
            log.info("Published package status update event for package: {}, status: {}", packageId, statusStr);
        } catch (Exception e) {
            log.error("Error processing delivery update for package: {}", packageId, e);
        }
    }

    /**
     * Process delivery completed notifications
     * 
     * @param deliveryCompleted The delivery completed message
     */
    private void processDeliveryCompleted(DeliveryCompleted deliveryCompleted) {
        String packageId = deliveryCompleted.getPackageId();
        
        log.info("Delivery completed for package: {}", packageId);
        
        try {
            // Publish event to update package status to DELIVERED
            PackageStatusUpdateEvent statusEvent = new PackageStatusUpdateEvent(packageId, "DELIVERED");
            eventPublisher.publish(statusEvent);
            
            log.info("Published package delivery completed event for package: {}", packageId);
        } catch (Exception e) {
            log.error("Error processing delivery completed for package: {}", packageId, e);
        }
    }

    /**
     * Process error messages from UPS
     * 
     * @param error The error message
     */
    private void processError(Error error) {
        long seqNum = error.getSeqnum();
        Error.ErrorType errorCode = error.getErrorCode();
        String errorMessage = error.getMessage();
        
        log.error("Error received from UPS - seqNum: {}, errorCode: {}, message: {}", 
                seqNum, errorCode, errorMessage);
        
        // Handle specific error types if needed
        switch (errorCode) {
            case TIMEOUT:
                log.error("UPS reported a timeout for seqNum: {}", seqNum);
                break;
            case DUPLICATE_SEQNUM:
                log.error("UPS reported a duplicate sequence number: {}", seqNum);
                break;
            case INVALID_TRUCK:
                log.error("UPS reported an invalid truck ID for seqNum: {}", seqNum);
                break;
            case MISSING_PACKAGE:
                log.error("UPS reported a missing package for seqNum: {}", seqNum);
                break;
            default:
                log.error("Unknown error code from UPS: {}", errorCode);
                break;
        }
    }
} 