package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Event triggered when an order is ready to be packed
 */
@Getter
public class PackOrderEvent extends DomainEvent {
    private final long orderId;
    private final int warehouseId;
    private final List<Map<String, Object>> items;
    private final String packageId;
    private final int destX;
    private final int destY;
    private final String upsAccount;

    public PackOrderEvent(long orderId, int warehouseId, List<Map<String, Object>> items, String packageId, 
                          int destX, int destY, String upsAccount) {
        super();
        this.orderId = orderId;
        this.warehouseId = warehouseId;
        this.items = items;
        this.packageId = packageId;
        this.destX = destX;
        this.destY = destY;
        this.upsAccount = upsAccount;
    }
} 