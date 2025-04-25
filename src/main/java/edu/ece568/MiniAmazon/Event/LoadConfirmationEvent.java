package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

/**
 * Event triggered when a package is loaded onto a truck
 */
@Getter
public class LoadConfirmationEvent extends DomainEvent {
    private final String packageId;
    private final int truckId;

    public LoadConfirmationEvent(String packageId, int truckId) {
        super();
        this.packageId = packageId;
        this.truckId = truckId;
    }
} 