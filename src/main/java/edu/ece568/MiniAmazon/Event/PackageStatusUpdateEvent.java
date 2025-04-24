package edu.ece568.MiniAmazon.Event;

import lombok.Getter;

/**
 * Event triggered when a package status is updated
 */
@Getter
public class PackageStatusUpdateEvent extends DomainEvent {
    private final String packageId;
    private final String status;

    public PackageStatusUpdateEvent(String packageId, String status) {
        super();
        this.packageId = packageId;
        this.status = status;
    }
} 