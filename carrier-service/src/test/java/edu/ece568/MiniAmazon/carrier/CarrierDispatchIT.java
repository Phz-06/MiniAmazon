package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.Events.ConfirmLoaded;
import edu.ece568.MiniAmazon.proto.Events.Envelope;
import edu.ece568.MiniAmazon.proto.Events.RequestPickup;
import edu.ece568.MiniAmazon.proto.Events.WorldAdopted;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import edu.ece568.MiniAmazon.shared.testing.Containers;
import edu.ece568.MiniAmazon.shared.testing.FakeWorld;
import edu.ece568.MiniAmazon.shared.testing.KafkaProbe;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.atomic.AtomicBoolean;

import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.await;
import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.envelope;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three trucks, eighteen packages, and the same hostility as the gateway
 * test: duplicated requests from Kafka, a world that loses and repeats
 * messages, and a connection that keeps dying. Every package must be picked
 * up and delivered exactly once, and the trucks must be reused, never
 * double-booked.
 */
@SpringBootTest(properties = {
        "app.carrier.trucks=3",
        "app.carrier.dispatch-interval-ms=100",
        "app.world.pump-interval-ms=20",
        "app.world.retry-base-ms=40",
        "app.outbox.pump-interval-ms=20",
        "app.outbox.retry-base-ms=100"})
@ActiveProfiles("test")
@Import(Containers.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CarrierDispatchIT {

    static final int TRUCKS = 3;
    static final int PACKAGES = 18;
    static final FakeWorld world = new FakeWorld();

    @DynamicPropertySource
    static void pointAtFakeWorld(DynamicPropertyRegistry registry) {
        registry.add("world.host", () -> "localhost");
        registry.add("world.port", world::upsPort);
    }

    @Autowired KafkaConnectionDetails kafka;
    @Autowired WorldCommandStore commands;
    @Autowired JdbcClient jdbc;

    KafkaProbe probe;

    @BeforeAll
    void start() {
        probe = new KafkaProbe(String.join(",", kafka.getBootstrapServers()), Topics.CARRIER_EVENTS);
        // Play order-service: when a truck is at the warehouse, the package gets loaded and we say so (twice).
        probe.respond((p, seen) -> {
            if (seen.envelope() != null && seen.envelope().hasTruckArrived()) {
                long orderId = seen.envelope().getTruckArrived().getOrderId();
                p.sendTwice(Topics.CARRIER_COMMANDS, seen.key(), envelope("os-loaded-" + seen.envelope().getId(), e -> e.setConfirmLoaded(
                        ConfirmLoaded.newBuilder().setOrderId(orderId).setTruckId(seen.envelope().getTruckArrived().getTruckId()))));
            }
        });
    }

    @AfterAll
    void stop() {
        probe.close();
        world.close();
    }

    @Test
    void everyPackageIsPickedUpAndDeliveredOnce_withFewerTrucksThanPackages() throws Exception {
        long worldId = world.createWorld();
        probe.sendTwice(Topics.WAREHOUSE_EVENTS, "world", envelope("gw-world-" + worldId,
                e -> e.setWorldAdopted(WorldAdopted.newBuilder().setWorldId(worldId).setGeneration(1))));
        await("the carrier to register its trucks in the announced world", () -> world.trucksRegistered() == TRUCKS);

        world.dropRate(0.3);
        AtomicBoolean chaos = new AtomicBoolean(true);
        Thread saboteur = new Thread(() -> {
            while (chaos.get()) {
                pause(150);
                world.killConnection();
            }
        });
        saboteur.start();

        for (long order = 1; order <= PACKAGES; order++) {
            long orderId = order;
            probe.sendTwice(Topics.CARRIER_COMMANDS, "order-" + order, envelope("os-pickup-" + order, e -> e.setRequestPickup(
                    RequestPickup.newBuilder().setOrderId(orderId).setWarehouseId(1).setDestX((int) orderId).setDestY(2))));
        }

        await("every package to be reported delivered",
                () -> probe.distinct(Topics.CARRIER_EVENTS, Envelope::hasDelivered) == PACKAGES);
        chaos.set(false);
        saboteur.join();
        await("the world to have acknowledged everything", () -> commands.count("PENDING") == 0);
        await("every truck to be back", () -> count("SELECT count(*) FROM truck WHERE status = 'IDLE'") == TRUCKS);

        assertThat(world.droppedIncoming()).as("commands the world ignored").isPositive();
        assertThat(world.connections()).as("connections the carrier had to open").isGreaterThan(3);

        // The world saw one pickup trip and one delivery per package, no more.
        assertThat(world.pickups()).as("pickup trips").isEqualTo(PACKAGES);
        for (long order = 1; order <= PACKAGES; order++) {
            long orderId = order;
            assertThat(world.timesDelivered(orderId)).as("deliveries of package %s", orderId).isEqualTo(1);
            assertThat(probe.distinct(Topics.CARRIER_EVENTS, e -> e.hasTruckArrived() && e.getTruckArrived().getOrderId() == orderId))
                    .as("'truck arrived' events for package %s", orderId).isEqualTo(1);
            assertThat(probe.distinct(Topics.CARRIER_EVENTS, e -> e.hasDelivered() && e.getDelivered().getOrderId() == orderId))
                    .as("'delivered' events for package %s", orderId).isEqualTo(1);
        }
        // Three trucks did all eighteen trips: they were reused, and a duplicated request never booked a second one.
        assertThat(count("SELECT count(*) FROM shipment WHERE status = 'DELIVERED'")).isEqualTo(PACKAGES);
        assertThat(count("SELECT count(DISTINCT truck_id) FROM shipment")).isEqualTo(TRUCKS);
        assertThat(commands.count("DONE")).isEqualTo(2L * PACKAGES);
        assertThat(commands.count("FAILED")).isZero();

        // The simulator restarts: the world is gone, and its replacement gets the same id.
        world.dropRate(0);
        world.loseAllWorlds();
        long reborn = world.createWorld();
        assertThat(reborn).as("the simulator reuses world ids").isEqualTo(worldId);
        probe.send(Topics.WAREHOUSE_EVENTS, "world", envelope("gw-world-reborn",
                e -> e.setWorldAdopted(WorldAdopted.newBuilder().setWorldId(reborn).setGeneration(2))));

        // Same id, higher generation: the carrier must notice and register its trucks again.
        await("the trucks to be registered in the new world", () -> world.trucksRegistered() == TRUCKS);
        long next = PACKAGES + 1;
        probe.send(Topics.CARRIER_COMMANDS, "order-" + next, envelope("os-pickup-" + next, e -> e.setRequestPickup(
                RequestPickup.newBuilder().setOrderId(next).setWarehouseId(1).setDestX(1).setDestY(1))));
        await("a package to be delivered in the new world", () -> world.timesDelivered(next) == 1);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
