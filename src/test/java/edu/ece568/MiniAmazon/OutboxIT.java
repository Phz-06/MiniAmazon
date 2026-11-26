package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.messaging.OutboxMessage;
import edu.ece568.MiniAmazon.support.IntegrationTest;
import edu.ece568.MiniAmazon.support.Race;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OutboxIT {

    @Autowired JdbcClient jdbc;

    /** A long backoff, so a message claimed in this test cannot come due again while it runs. */
    private Outbox outbox() {
        return new Outbox(jdbc, 600_000);
    }

    @Test
    void eightRelaysPollingAtOnce_neverReceiveTheSameMessage() throws Exception {
        Outbox outbox = outbox();
        Set<Long> mine = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            mine.add(outbox.enqueue(Destination.CARRIER, "TEST", null, id -> new byte[]{1}));
        }

        ConcurrentLinkedQueue<Long> claimed = new ConcurrentLinkedQueue<>();
        List<Callable<?>> relays = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            relays.add(() -> {
                List<OutboxMessage> batch;
                while (!(batch = outbox.claimDue(Destination.CARRIER, 20)).isEmpty()) {
                    batch.forEach(m -> claimed.add(m.id()));
                }
                return null;
            });
        }
        assertThat(Race.run(relays).failures()).isEmpty();
        // A relay may stop early when every remaining row is locked by another; one more pass picks up stragglers.
        outbox.claimDue(Destination.CARRIER, 1000).forEach(m -> claimed.add(m.id()));

        List<Long> claimedMine = claimed.stream().filter(mine::contains).toList();
        assertThat(claimedMine).doesNotHaveDuplicates();
        assertThat(new HashSet<>(claimedMine)).isEqualTo(mine);
        mine.forEach(outbox::markDone);
    }

    @Test
    void aClaimedMessageIsNotHandedOutAgainBeforeItsBackoff_andFinishesOnlyOnce() {
        Outbox outbox = outbox();
        long id = outbox.enqueue(Destination.CARRIER, "TEST", null, seq -> new byte[]{2});

        assertThat(outbox.claimDue(Destination.CARRIER, 1000)).extracting(OutboxMessage::id).contains(id);
        assertThat(outbox.claimDue(Destination.CARRIER, 1000)).extracting(OutboxMessage::id).doesNotContain(id);

        assertThat(outbox.markDone(id)).isTrue();
        assertThat(outbox.markDone(id)).as("a duplicate acknowledgement").isFalse();
    }

    @Test
    void aRefusedMessageWaitsForAnOperator_andCanBeSentAgain() {
        Outbox outbox = outbox();
        long id = outbox.enqueue(Destination.CARRIER, "TEST", null, seq -> new byte[]{3});
        outbox.claimDue(Destination.CARRIER, 1000);

        assertThat(outbox.markFailed(id, "refused")).isTrue();
        assertThat(outbox.listFailed(100)).anyMatch(row -> ((Number) row.get("id")).longValue() == id);
        assertThat(outbox.claimDue(Destination.CARRIER, 1000)).extracting(OutboxMessage::id).doesNotContain(id);

        assertThat(outbox.retry(id)).isTrue();
        assertThat(outbox.claimDue(Destination.CARRIER, 1000)).extracting(OutboxMessage::id).contains(id);
        outbox.markDone(id);
    }
}
