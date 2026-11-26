package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.WorldUps.UDeliveryMade;
import edu.ece568.MiniAmazon.proto.WorldUps.UErr;
import edu.ece568.MiniAmazon.proto.WorldUps.UFinished;
import edu.ece568.MiniAmazon.proto.WorldUps.UResponses;
import edu.ece568.MiniAmazon.proto.WorldUps.UTruck;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import edu.ece568.MiniAmazon.shared.worldlink.WorldInbox;
import edu.ece568.MiniAmazon.shared.worldlink.WorldLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/** Applies what the world reports about our trucks, each report at most once. */
@Component
public class UpsResponseHandler implements WorldLink.Listener<UResponses> {
    private static final Logger log = LoggerFactory.getLogger(UpsResponseHandler.class);

    private final WorldInbox inbox;
    private final DispatchService dispatch;
    private final WorldCommandStore commands;
    private final TransactionTemplate tx;

    public UpsResponseHandler(WorldInbox inbox, DispatchService dispatch, WorldCommandStore commands, TransactionTemplate tx) {
        this.inbox = inbox;
        this.dispatch = dispatch;
        this.commands = commands;
        this.tx = tx;
    }

    @Override
    public List<Long> onResponses(long worldId, UResponses responses) {
        List<Long> applied = new ArrayList<>();
        for (UFinished finished : responses.getCompletionsList()) {
            if (once(worldId, finished.getSeqnum(), "FINISHED",
                    () -> dispatch.truckFinished(finished.getTruckid(), finished.getStatus()))) {
                applied.add(finished.getSeqnum());
            }
        }
        for (UDeliveryMade made : responses.getDeliveredList()) {
            if (once(worldId, made.getSeqnum(), "DELIVERED", () -> dispatch.delivered(made.getPackageid()))) {
                applied.add(made.getSeqnum());
            }
        }
        for (UErr error : responses.getErrorList()) {
            if (once(worldId, error.getSeqnum(), "ERROR", () -> {
                log.error("World rejected carrier command {}: {}", error.getOriginseqnum(), error.getErr());
                commands.markFailed(error.getOriginseqnum(), error.getErr());
            })) {
                applied.add(error.getSeqnum());
            }
        }
        for (UTruck status : responses.getTruckstatusList()) {
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
}
