package edu.ece568.MiniAmazon.gateway;

import edu.ece568.MiniAmazon.proto.Events.Envelope;
import edu.ece568.MiniAmazon.proto.Events.Item;
import edu.ece568.MiniAmazon.proto.Events.LoadPackage;
import edu.ece568.MiniAmazon.proto.Events.PackOrder;
import edu.ece568.MiniAmazon.proto.Events.PurchaseStock;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.await;
import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.envelope;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway between two unreliable neighbours: Kafka delivers every command
 * twice, and the world loses 30% of messages in each direction, repeats
 * everything unacknowledged every 25 ms and has its connection cut every
 * 150 ms. Each command must still take effect in the world exactly once and
 * be reported on Kafka as exactly one event.
 */
@SpringBootTest(properties = {
        "app.world.pump-interval-ms=20",
        "app.world.retry-base-ms=40",
        "app.outbox.pump-interval-ms=20",
        "app.outbox.retry-base-ms=100"})
@ActiveProfiles("test")
@Import(Containers.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayReliabilityIT {

    static final FakeWorld world = new FakeWorld();

    @DynamicPropertySource
    static void pointAtFakeWorld(DynamicPropertyRegistry registry) {
        registry.add("world.host", () -> "localhost");
        registry.add("world.port", world::port);
    }

    @Autowired KafkaConnectionDetails kafka;
    @Autowired WorldCommandStore commands;

    KafkaProbe probe;

    @BeforeAll
    void start() {
        probe = new KafkaProbe(String.join(",", kafka.getBootstrapServers()), Topics.WAREHOUSE_EVENTS);
    }

    @AfterAll
    void stop() {
        probe.close();
        world.close();
    }

    @Test
    void everyCommandTakesEffectOnce_andIsReportedOnce() throws Exception {
        // With no world on record the gateway creates one and tells everybody, without being asked.
        await("the gateway to announce its world", () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasWorldAdopted) == 1);

        // The other test in this class shares the database; count only what this one adds.
        long doneBefore = commands.count("DONE");
        long failedBefore = commands.count("FAILED");

        world.dropRate(0.3);
        AtomicBoolean chaos = new AtomicBoolean(true);
        Thread saboteur = new Thread(() -> {
            while (chaos.get()) {
                pause(150);
                world.killConnection();
            }
        });
        saboteur.start();

        int products = 5, perProduct = 100, orders = 40;
        for (int p = 1; p <= products; p++) {
            long productId = p;
            probe.sendTwice(Topics.WAREHOUSE_COMMANDS, "product-" + p, envelope("test-buy-" + p, e -> e.setPurchaseStock(
                    PurchaseStock.newBuilder().setWarehouseId(1).addItems(item(productId, perProduct)))));
        }
        await("all purchases to be reported", () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasStockArrived) == products);

        List<Long> orderIds = new ArrayList<>();
        for (long order = 1; order <= orders; order++) {
            long orderId = order;
            long productId = 1 + order % products;
            probe.sendTwice(Topics.WAREHOUSE_COMMANDS, "order-" + order, envelope("test-pack-" + order, e -> e.setPackOrder(
                    PackOrder.newBuilder().setOrderId(orderId).setWarehouseId(1).addItems(item(productId, 2)))));
            orderIds.add(orderId);
        }
        await("every order to be reported packed",
                () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasOrderPacked) == orders);

        for (long order : orderIds) {
            probe.sendTwice(Topics.WAREHOUSE_COMMANDS, "order-" + order, envelope("test-load-" + order, e -> e.setLoadPackage(
                    LoadPackage.newBuilder().setOrderId(order).setWarehouseId(1).setTruckId(3))));
        }
        await("every order to be reported loaded",
                () -> probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasPackageLoaded) == orders);

        chaos.set(false);
        saboteur.join();
        await("the world to have acknowledged everything", () -> commands.count("PENDING") == 0);

        // The test really was hostile.
        assertThat(world.droppedIncoming()).as("commands the world ignored").isPositive();
        assertThat(world.redeliveries()).as("messages the world repeated").isPositive();
        assertThat(world.connections()).as("connections the gateway had to open").isGreaterThan(3);

        // Exactly once in the world...
        for (long order : orderIds) {
            assertThat(world.timesPacked(order)).as("packs of order %s", order).isEqualTo(1);
            assertThat(world.timesLoaded(order)).as("loads of order %s", order).isEqualTo(1);
        }
        for (long p = 1; p <= products; p++) {
            assertThat(world.stockOf(p)).as("world stock of product %s", p).isEqualTo(perProduct - 2 * (orders / products));
        }
        // ...and exactly one event for each thing that happened.
        for (long order : orderIds) {
            assertThat(probe.distinct(Topics.WAREHOUSE_EVENTS, e -> e.hasOrderPacked() && e.getOrderPacked().getOrderId() == order)).isEqualTo(1);
            assertThat(probe.distinct(Topics.WAREHOUSE_EVENTS, e -> e.hasPackageLoaded() && e.getPackageLoaded().getOrderId() == order)).isEqualTo(1);
        }
        int arrived = probe.envelopes(Topics.WAREHOUSE_EVENTS, Envelope::hasStockArrived).stream()
                .collect(java.util.stream.Collectors.toMap(Envelope::getId, e -> e.getStockArrived().getItems(0).getCount(), (a, b) -> a))
                .values().stream().mapToInt(Integer::intValue).sum();
        assertThat(arrived).as("units reported arrived").isEqualTo(products * perProduct);
        // A duplicated Kafka command never became a second world command.
        assertThat(commands.count("DONE") - doneBefore).isEqualTo(products + 2L * orders);
        assertThat(commands.count("FAILED") - failedBefore).isZero();
        assertThat(probe.distinct(Topics.WAREHOUSE_EVENTS, Envelope::hasWorldAdopted)).as("the world was never replaced").isEqualTo(1);
    }

    @Test
    void aCommandTheWorldRefuses_isReportedWithItsReason_andNotRetried() {
        long orderId = 9001;
        probe.send(Topics.WAREHOUSE_COMMANDS, "order-" + orderId, envelope("test-pack-nothing-" + orderId, e -> e.setPackOrder(
                PackOrder.newBuilder().setOrderId(orderId).setWarehouseId(1).addItems(item(777, 5)))));

        await("the rejection to be reported", () -> probe.distinct(Topics.WAREHOUSE_EVENTS,
                e -> e.hasCommandRejected() && e.getCommandRejected().getOrderId() == orderId) == 1);

        Envelope rejection = probe.envelopes(Topics.WAREHOUSE_EVENTS,
                e -> e.hasCommandRejected() && e.getCommandRejected().getOrderId() == orderId).get(0);
        assertThat(rejection.getCommandRejected().getCommand()).isEqualTo("PACK");
        assertThat(rejection.getCommandRejected().getReason()).contains("not enough stock");
        assertThat(world.timesPacked(orderId)).isZero();
    }

    private static Item item(long productId, int count) {
        return Item.newBuilder().setProductId(productId).setDescription("product " + productId).setCount(count).build();
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
