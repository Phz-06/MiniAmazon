package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.Amazon_UPS.Coordinates;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.LoadConfirmation;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.ShipmentRequest;

/** Messages to the carrier as they are stored in the outbox. The package id is the order id. */
public final class CarrierMessages {
    public static final String REQUEST_PICKUP = "REQUEST_PICKUP";
    public static final String CONFIRM_LOADED = "CONFIRM_LOADED";

    private CarrierMessages() {
    }

    public static byte[] requestPickup(long orderId, int warehouseId, int destX, int destY, String account) {
        ShipmentRequest.Builder request = ShipmentRequest.newBuilder()
                .setPackageId(String.valueOf(orderId))
                .setWarehouseId(warehouseId)
                .setDestination(Coordinates.newBuilder().setX(destX).setY(destY));
        if (account != null && !account.isEmpty()) {
            request.setUpsAccount(account);
        }
        return request.build().toByteArray();
    }

    public static byte[] confirmLoaded(long orderId, Integer truckId) {
        return LoadConfirmation.newBuilder()
                .setPackageId(String.valueOf(orderId))
                .setTruckId(truckId == null ? 0 : truckId)
                .build().toByteArray();
    }
}
