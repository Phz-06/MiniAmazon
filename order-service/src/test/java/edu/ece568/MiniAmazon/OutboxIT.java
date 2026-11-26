package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.proto.Events.OrderPacked;
import edu.ece568.MiniAmazon.shared.messaging.EventOutbox;
import edu.ece568.MiniAmazon.shared.messaging.OutboxRecord;
import edu.ece568.MiniAmazon.shared.testing.Race;
import edu.ece568.MiniAmazon.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/** The service's own relay is parked for this class, so the test is the only one claiming rows. */
@IntegrationTest
@TestPropertySource(properties = "app.outbox.pump-interval-ms=3600000")
class OutboxIT {

    @Autowired JdbcClient jdbc;

    /** A long backoff, so a row claimed in this test cannot come due again while it runs. */
    private EventOutbox outbox() {
        return new EventOutbox(jdbc, "test", 600_000);
    }

    private long publish(EventOutbox outbox) {
        return outbox.publish("test.topic", "k", e -> e.setOrderPacked(OrderPacked.newBuilder().setOrderId(1)));
    }

    @Test
    void eightRelaysPollingAtOnce_neverReceiveTheSameRow() throws Exception {
        EventOutbox outbox = outbox();
        Set<Long> mine = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            mine.add(publish(outbox));
        }

        ConcurrentLinkedQueue<Long> claimed = new ConcurrentLinkedQueue<>();
        List<Callable<?>> relays = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            relays.add(() -> {
                List<OutboxRecord> batch;
                while (!(batch = outbox.claimDue(20)).isEmpty()) {
                    batch.forEach(r -> claimed.add(r.id()));
                }
                return null;
            });
        }
        assertThat(Race.run(relays).failures()).isEmpty();
        // A relay may stop early when every remaining row is locked by another; one more pass picks up stragglers.
        outbox.claimDue(1000).forEach(r -> claimed.add(r.id()));

        List<Long> claimedMine = claimed.stream().filter(mine::contains).toList();
        assertThat(claimedMine).doesNotHaveDuplicates();
        assertThat(new HashSet<>(claimedMine)).isEqualTo(mine);
        mine.forEach(outbox::markDone);
    }

    @Test
    void aClaimedRowIsNotHandedOutAgainBeforeItsBackoff_andFinishesOnlyOnce() {
        EventOutbox outbox = outbox();
        long id = publish(outbox);

        assertThat(outbox.claimDue(1000)).extracting(OutboxRecord::id).contains(id);
        assertThat(outbox.claimDue(1000)).extracting(OutboxRecord::id).doesNotContain(id);

        assertThat(outbox.markDone(id)).isTrue();
        assertThat(outbox.markDone(id)).as("a duplicate acknowledgement").isFalse();
    }

    @Test
    void theMessageIdIsTheServiceNameAndRowId_soAResendCarriesTheSameId() throws Exception {
        EventOutbox outbox = outbox();
        long id = publish(outbox);
        OutboxRecord row = outbox.claimDue(1000).stream().filter(r -> r.id() == id).findFirst().orElseThrow();

        assertThat(edu.ece568.MiniAmazon.proto.Events.Envelope.parseFrom(row.payload()).getId()).isEqualTo("test-" + id);
        outbox.markDone(id);
    }
}
