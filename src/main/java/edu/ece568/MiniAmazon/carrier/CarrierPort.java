package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.Amazon_UPS.LoadConfirmation;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.ShipmentRequest;

import java.io.IOException;

/**
 * What we need from a shipping company. The carrier answers through
 * FulfillmentService (onTruckAssigned, onDelivered).
 */
public interface CarrierPort {

    /** False while there is nobody to talk to; messages then wait in the outbox. */
    boolean isAvailable();

    void requestPickup(ShipmentRequest request) throws IOException;

    void confirmLoaded(LoadConfirmation confirmation) throws IOException;
}
