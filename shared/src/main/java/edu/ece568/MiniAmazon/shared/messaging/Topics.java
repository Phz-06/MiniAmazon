package edu.ece568.MiniAmazon.shared.messaging;

/** Kafka topics. See events.proto for what travels on each. */
public final class Topics {
    public static final String WAREHOUSE_COMMANDS = "warehouse.commands";
    public static final String WAREHOUSE_EVENTS = "warehouse.events";
    public static final String CARRIER_COMMANDS = "carrier.commands";
    public static final String CARRIER_EVENTS = "carrier.events";

    /** Where a record goes after its consumer has given up on it. */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    private Topics() {
    }
}
