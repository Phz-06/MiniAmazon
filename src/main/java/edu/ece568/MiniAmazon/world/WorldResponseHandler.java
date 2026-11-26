package edu.ece568.MiniAmazon.world;

import edu.ece568.MiniAmazon.fulfillment.FulfillmentService;
import edu.ece568.MiniAmazon.fulfillment.ReplenishmentService;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.messaging.WorldInbox;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AErr;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ALoaded;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APackage;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APacked;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies what the world tells us, and decides what may be acknowledged.
 *
 * Each message runs in its own transaction together with its inbox row. A
 * message is acknowledged only once that transaction has committed (now or on
 * an earlier delivery). If applying it fails we stay silent, and the world's
 * own retry gives us another attempt.
 */
@Slf4j
@Component
public class WorldResponseHandler {

    private final Outbox outbox;
    private final WorldInbox inbox;
    private final FulfillmentService fulfillment;
    private final ReplenishmentService replenishment;
    private final TransactionTemplate tx;

    public WorldResponseHandler(Outbox outbox, WorldInbox inbox, FulfillmentService fulfillment,
                                ReplenishmentService replenishment, TransactionTemplate tx) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.fulfillment = fulfillment;
        this.replenishment = replenishment;
        this.tx = tx;
    }

    /** @return sequence numbers of the world's messages that are safely applied and can be acknowledged */
    public List<Long> handle(long worldId, AResponses responses) {
        // The world acknowledging our commands: they are finished.
        for (long acked : responses.getAcksList()) {
            outbox.markDone(acked);
        }

        List<Long> applied = new ArrayList<>();
        for (APurchaseMore arrived : responses.getArrivedList()) {
            if (once(worldId, arrived.getSeqnum(), "ARRIVED", () -> replenishment.received(arrived))) {
                applied.add(arrived.getSeqnum());
            }
        }
        for (APacked packed : responses.getReadyList()) {
            if (once(worldId, packed.getSeqnum(), "PACKED", () -> fulfillment.onPacked(packed.getShipid()))) {
                applied.add(packed.getSeqnum());
            }
        }
        for (ALoaded loaded : responses.getLoadedList()) {
            if (once(worldId, loaded.getSeqnum(), "LOADED", () -> fulfillment.onLoaded(loaded.getShipid()))) {
                applied.add(loaded.getSeqnum());
            }
        }
        for (AErr error : responses.getErrorList()) {
            if (once(worldId, error.getSeqnum(), "ERROR", () -> rejected(error))) {
                applied.add(error.getSeqnum());
            }
        }
        for (APackage status : responses.getPackagestatusList()) {
            applied.add(status.getSeqnum());
        }
        return applied;
    }

    private boolean once(long worldId, long seqnum, String kind, Runnable effect) {
        try {
            tx.executeWithoutResult(status -> {
                if (inbox.firstTime(worldId, seqnum, kind)) {
                    effect.run();
                }
            });
            return true;
        } catch (RuntimeException e) {
            log.error("Could not apply world message {} ({}); leaving it unacknowledged", seqnum, kind, e);
            return false;
        }
    }

    /** The world understood a command and refused it. Resending the same thing would be refused again. */
    private void rejected(AErr error) {
        log.error("World rejected command {}: {}", error.getOriginseqnum(), error.getErr());
        outbox.markFailed(error.getOriginseqnum(), error.getErr());
    }
}
