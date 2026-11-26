package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.fulfillment.ReplenishmentService;
import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.PaymentService;
import edu.ece568.MiniAmazon.support.FakeCarrier;
import edu.ece568.MiniAmazon.support.FakeWorld;
import edu.ece568.MiniAmazon.support.Fixtures;
import edu.ece568.MiniAmazon.support.IntegrationTest;
import edu.ece568.MiniAmazon.world.WorldGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim this project makes: an order is fulfilled exactly once even though
 * the systems it depends on lose messages, repeat messages, refuse requests
 * and drop the connection.
 */
@IntegrationTest
@Import(FakeCarrier.class)
@TestPropertySource(properties = {
        "app.world.pump-interval-ms=20",
        "app.carrier.pump-interval-ms=20",
        "app.outbox.retry-base-ms=40"})
class FulfillmentReliabilityIT {

    static final FakeWorld world = new FakeWorld();

    @DynamicPropertySource
    static void pointAtFakeWorld(DynamicPropertyRegistry registry) {
        registry.add("world.host", () -> "localhost");
        registry.add("world.port", world::port);
    }

    @AfterAll
    static void stopWorld() {
        world.close();
    }

    @Autowired WorldGateway gateway;
    @Autowired ReplenishmentService replenishment;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired Outbox outbox;
    @Autowired FakeCarrier carrier;
    @Autowired Fixtures fixtures;
    @Autowired JdbcClient jdbc;

    @Test
    void everyOrderIsFulfilledExactlyOnce_thoughAThirdOfAllMessagesAreLost_andTheConnectionKeepsDying() throws Exception {
        world.dropRate(0.3);
        gateway.connect(null, true);

        // Stock is bought from the world and is sellable only once it arrives.
        int perProduct = 60;
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Product product = fixtures.product("10.00", 0);
            replenishment.purchase(WorldGateway.WAREHOUSE_ID, Long.parseLong(product.getProductId()), product.getName(), perProduct);
            products.add(product);
        }
        await("purchased stock to arrive", () -> products.stream().allMatch(p -> fixtures.stockOf(p) == perProduct));

        AtomicBoolean chaos = new AtomicBoolean(true);
        Thread saboteur = new Thread(() -> {
            while (chaos.get()) {
                sleep(150);
                world.killConnection();
            }
        });
        saboteur.start();

        Random random = new Random(3);
        User buyer = fixtures.user();
        Map<String, Integer> sold = new HashMap<>();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Product first = products.get(random.nextInt(products.size()));
            Product second = products.get(random.nextInt(products.size()));
            int q1 = 1 + random.nextInt(2);
            int q2 = 1 + random.nextInt(2);
            Order order = fixtures.confirmedOrder(buyer, first, q1, second, q2);
            sold.merge(first.getProductId(), q1, Integer::sum);
            sold.merge(second.getProductId(), q2, Integer::sum);
            paymentService.processPayment(order.getId(), orderService.getOrder(order.getId()).getTotalPrice());
            orders.add(order);
            sleep(10);
        }

        await("every order to be delivered",
                () -> orders.stream().allMatch(o -> orderService.getOrder(o.getId()).getStatus() == Status.DELIVERED));
        chaos.set(false);
        saboteur.join();
        await("the outbox to drain", () -> outbox.count(Destination.WORLD, "PENDING") == 0
                && outbox.count(Destination.CARRIER, "PENDING") == 0);

        // The test really was hostile.
        assertThat(world.droppedIncoming()).as("commands the world ignored").isPositive();
        assertThat(world.redeliveries()).as("messages the world sent more than once").isPositive();
        assertThat(world.connections()).as("connections the gateway had to open").isGreaterThan(3);
        assertThat(carrier.refusals()).as("requests the carrier refused").isPositive();

        // Exactly once, as the warehouse saw it.
        for (Order order : orders) {
            assertThat(world.timesPacked(order.getId())).as("packs of order %s", order.getId()).isEqualTo(1);
            assertThat(world.timesLoaded(order.getId())).as("loads of order %s", order.getId()).isEqualTo(1);
            // The duplicate truck assignment (a different truck) did not replace the first.
            assertThat(orderService.getOrder(order.getId()).getTruckId()).isEqualTo(carrier.truckFor(order.getId()));
        }
        // Stock is conserved: what we hold, what the warehouse holds and what was sold add up.
        for (Product product : products) {
            int expected = perProduct - sold.getOrDefault(product.getProductId(), 0);
            assertThat(fixtures.stockOf(product)).as("our stock of %s", product.getProductId()).isEqualTo(expected);
            assertThat(world.stockOf(Long.parseLong(product.getProductId()))).as("world stock of %s", product.getProductId()).isEqualTo(expected);
        }
        // One step per order and nothing refused: no duplicate commands were ever created.
        assertThat(outbox.count(Destination.WORLD, "FAILED")).isZero();
        assertThat(commands("PACK")).isEqualTo(orders.size());
        assertThat(commands("LOAD")).isEqualTo(orders.size());
    }

    private long commands(String type) {
        return jdbc.sql("SELECT count(*) FROM outbox_message WHERE type = :type").param("type", type)
                .query(Long.class).single();
    }

    private static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            sleep(50);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
