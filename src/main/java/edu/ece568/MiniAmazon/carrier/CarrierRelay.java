package edu.ece568.MiniAmazon.carrier;

import com.google.protobuf.InvalidProtocolBufferException;
import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.messaging.OutboxMessage;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.LoadConfirmation;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.ShipmentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Delivers outbox messages to the carrier.
 *
 * A message is finished once the carrier accepted it. If the carrier is away
 * or the send fails, the message stays pending and is retried with backoff,
 * so the carrier can receive the same request more than once and must treat
 * the package id as an idempotency key (ours does the same in the other
 * direction: see FulfillmentService).
 */
@Slf4j
@Component
public class CarrierRelay {

    private final Outbox outbox;
    private final CarrierPort carrier;

    public CarrierRelay(Outbox outbox, CarrierPort carrier) {
        this.outbox = outbox;
        this.carrier = carrier;
    }

    @Scheduled(fixedDelayString = "${app.carrier.pump-interval-ms:200}")
    public void pump() {
        if (!carrier.isAvailable()) {
            return;
        }
        for (OutboxMessage message : outbox.claimDue(Destination.CARRIER, 50)) {
            try {
                switch (message.type()) {
                    case CarrierMessages.REQUEST_PICKUP ->
                            carrier.requestPickup(ShipmentRequest.parseFrom(message.payload()));
                    case CarrierMessages.CONFIRM_LOADED ->
                            carrier.confirmLoaded(LoadConfirmation.parseFrom(message.payload()));
                    default -> throw new InvalidProtocolBufferException("Unknown carrier message type " + message.type());
                }
                outbox.markDone(message.id());
            } catch (InvalidProtocolBufferException e) {
                outbox.markFailed(message.id(), "Unreadable payload: " + e.getMessage());
            } catch (IOException | RuntimeException e) {
                log.warn("Carrier did not take message {} (attempt {}): {}", message.id(), message.attempts(), e.getMessage());
            }
        }
    }
}
