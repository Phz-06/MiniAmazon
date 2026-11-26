package edu.ece568.MiniAmazon.gateway;

import edu.ece568.MiniAmazon.proto.Events.Envelope;
import edu.ece568.MiniAmazon.proto.Events.Item;
import edu.ece568.MiniAmazon.proto.Events.PurchaseStock;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import edu.ece568.MiniAmazon.shared.testing.Containers;
import edu.ece568.MiniAmazon.shared.testing.FakeWorld;
import edu.ece568.MiniAmazon.shared.testing.KafkaProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.await;
import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.envelope;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The simulator forgets every world when it restarts. The gateway must not
 * hammer on a world that will never answer again: after being refused for
 * long enough it creates a new one and says so.
 */
@SpringBootTest(properties = {
        "app.world.pump-interval-ms=20",
        "app.world.retry-base-ms=40",
        "app.outbox.pump-interval-ms=20",
        "app.world.recreate-after-refused-ms=2500"})
@ActiveProfiles("test")
@Import(Containers.class)
class GatewayWorldLossIT {

    static final FakeWorld world = new FakeWorld();

    @DynamicPropertySource
    static void pointAtFakeWorld(DynamicPropertyRegistry registry) {
        registry.add("world.host", () -> "localhost");
        registry.add("world.port", world::port);
    }

    @Autowired KafkaConnectionDetails kafka;

    @Test
    void whenItsWorldIsGone_theGatewayCreatesANewOne_andKeepsWorking() {
        try (KafkaProbe probe = new KafkaProbe(String.join(",", kafka.getBootstrapServers()), Topics.WAREHOUSE_EVENTS)) {
            await("the first world", () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasWorldAdopted) == 1);
            long first = probe.envelopes(Topics.WAREHOUSE_EVENTS, Envelope::hasWorldAdopted).get(0).getWorldAdopted().getWorldId();

            // Something happens in the first world, so its message number 1 is on record.
            probe.send(Topics.WAREHOUSE_COMMANDS, "product-4", envelope("test-buy-before-loss", e -> e.setPurchaseStock(
                    PurchaseStock.newBuilder().setWarehouseId(1)
                            .addItems(Item.newBuilder().setProductId(4).setDescription("four").setCount(3)))));
            await("stock bought in the first world to arrive",
                    () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasStockArrived) == 1);

            // A dropped connection alone is not a lost world: the gateway rejoins the same one.
            world.killConnection();
            pause(1500);
            assertThat(probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasWorldAdopted)).isEqualTo(1);

            world.loseAllWorlds();

            await("a second world to be announced", () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasWorldAdopted) == 2);
            // The new world has the same id as the lost one (the simulator starts counting again),
            // so the announcement has to be told apart by its generation.
            List<Envelope> announced = probe.envelopes(Topics.WAREHOUSE_EVENTS, Envelope::hasWorldAdopted);
            assertThat(announced).extracting(e -> e.getWorldAdopted().getWorldId()).containsOnly(first);
            assertThat(announced).extracting(e -> e.getWorldAdopted().getGeneration()).contains(1L, 2L);

            probe.send(Topics.WAREHOUSE_COMMANDS, "product-5", envelope("test-buy-after-loss", e -> e.setPurchaseStock(
                    PurchaseStock.newBuilder().setWarehouseId(1)
                            .addItems(Item.newBuilder().setProductId(5).setDescription("five").setCount(7)))));
            // Its arrival is the new world's message number 1 again. It must not be mistaken
            // for a repeat of the old world's number 1.
            await("stock bought in the new world to arrive",
                    () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasStockArrived) == 2);
            assertThat(world.stockOf(5)).isEqualTo(7);
        } finally {
            world.close();
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
