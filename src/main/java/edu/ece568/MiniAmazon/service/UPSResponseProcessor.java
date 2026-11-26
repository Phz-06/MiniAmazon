package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.fulfillment.FulfillmentService;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.DeliveryUpdate;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.GenericMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Turns messages from UPS into fulfilment steps. Every step is idempotent
 * (see FulfillmentService), so a message UPS sends twice changes nothing the
 * second time.
 */
@Service
@Slf4j
public class UPSResponseProcessor {

    private final UPSService upsService;
    private final FulfillmentService fulfillment;

    public UPSResponseProcessor(UPSService upsService, FulfillmentService fulfillment) {
        this.upsService = upsService;
        this.fulfillment = fulfillment;
    }

    @Scheduled(fixedDelay = 100)
    public void processResponses() {
        GenericMessage message;
        while ((message = upsService.getNextMessage()) != null) {
            try {
                process(message);
            } catch (RuntimeException e) {
                log.error("Could not process UPS message of type {}", message.getType(), e);
            }
        }
    }

    private void process(GenericMessage message) {
        switch (message.getType()) {
            case "truck_assignment" -> fulfillment.onTruckAssigned(
                    orderId(message.getTruckAssignment().getPackageId()), message.getTruckAssignment().getTruckId());
            case "delivery_update" -> {
                if (message.getDeliveryUpdate().getStatus() != DeliveryUpdate.Status.PACKING) {
                    fulfillment.onOutForDelivery(orderId(message.getDeliveryUpdate().getPackageId()));
                }
            }
            case "delivery_completed" -> fulfillment.onDelivered(orderId(message.getDeliveryCompleted().getPackageId()));
            case "error" -> log.error("UPS error {} for seqnum {}: {}", message.getError().getErrorCode(),
                    message.getError().getSeqnum(), message.getError().getMessage());
            default -> log.warn("Unknown message type from UPS: {}", message.getType());
        }
    }

    /** The package id we hand to UPS is the order id. */
    private static long orderId(String packageId) {
        return Long.parseLong(packageId.trim());
    }
}
