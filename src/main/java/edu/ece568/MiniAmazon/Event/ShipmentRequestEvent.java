package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

/**
 * Event triggered when a package is packed and ready to be shipped
 */
@Getter
public class ShipmentRequestEvent extends DomainEvent {
    private final String packageId;
    private final int warehouseId;
    private final int destX;
    private final int destY;
    private final String upsAccount;

    public ShipmentRequestEvent(String packageId, int warehouseId, int destX, int destY, String upsAccount) {
        super();
        this.packageId = packageId;
        this.warehouseId = warehouseId;
        this.destX = destX;
        this.destY = destY;
        this.upsAccount = upsAccount;
    }
} 