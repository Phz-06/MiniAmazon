package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

/**
 * Event triggered when a truck is assigned by UPS to load a package
 */
@Getter
public class LoadPackageEvent extends DomainEvent {
    private final String packageId;
    private final int truckId;

    public LoadPackageEvent(String packageId, int truckId) {
        super();
        this.packageId = packageId;
        this.truckId = truckId;
    }
} 