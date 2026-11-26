package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.fulfillment.ReplenishmentService;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.proto.Events.Delivered;
import edu.ece568.MiniAmazon.proto.Events.Envelope;
import edu.ece568.MiniAmazon.proto.Events.OrderPacked;
import edu.ece568.MiniAmazon.proto.Events.OutForDelivery;
import edu.ece568.MiniAmazon.proto.Events.PackageLoaded;
import edu.ece568.MiniAmazon.proto.Events.StockArrived;
import edu.ece568.MiniAmazon.proto.Events.TruckArrived;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.PaymentService;
import edu.ece568.MiniAmazon.shared.messaging.Topics;
import edu.ece568.MiniAmazon.shared.testing.KafkaProbe;
import edu.ece568.MiniAmazon.support.Fixtures;
import edu.ece568.MiniAmazon.support.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.await;
import static edu.ece568.MiniAmazon.shared.testing.KafkaProbe.envelope;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * order-service on its own, with Kafka as its only window on the world. The
 * test plays the warehouse gateway and the carrier, and plays them badly:
 * every reply is delivered twice, and the carrier also sends a second,
 * different answer to the same request.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {"app.outbox.pump-interval-ms=20", "app.kafka.retry-initial-ms=20"})
class FulfillmentMessagingIT {

    static final int REAL_TRUCK = 7;
    static final int IMPOSTOR_TRUCK = 99;

    @Autowired KafkaConnectionDetails kafka;
    @Autowired ReplenishmentService replenishment;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired Fixtures fixtures;
    @Autowired edu.ece568.MiniAmazon.shared.worldlink.WorldSession worldSession;

    KafkaProbe probe;

    @BeforeAll
    void startPeers() {
        probe = new KafkaProbe(String.join(",", kafka.getBootstrapServers()),
                Topics.WAREHOUSE_COMMANDS, Topics.CARRIER_COMMANDS, Topics.WAREHOUSE_EVENTS + Topics.DEAD_LETTER_SUFFIX);
        probe.respond(FulfillmentMessagingIT::actAsGatewayAndCarrier);
    }

    @AfterAll
    void stopPeers() {
        probe.close();
    }

    /** Reply ids are derived from the request id, so answering a duplicated request produces the same reply again. */
    static void actAsGatewayAndCarrier(KafkaProbe probe, KafkaProbe.Seen seen) {
        Envelope request = seen.envelope();
        if (request == null) {
            return;
        }
        String id = request.getId();
        switch (request.getBodyCase()) {
            case PURCHASE_STOCK -> probe.sendTwice(Topics.WAREHOUSE_EVENTS, seen.key(), envelope("gw-arrived-" + id,
                    e -> e.setStockArrived(StockArrived.newBuilder()
                            .setWarehouseId(request.getPurchaseStock().getWarehouseId())
                            .addAllItems(request.getPurchaseStock().getItemsList()))));
            case PACK_ORDER -> probe.sendTwice(Topics.WAREHOUSE_EVENTS, seen.key(), envelope("gw-packed-" + id,
                    e -> e.setOrderPacked(OrderPacked.newBuilder().setOrderId(request.getPackOrder().getOrderId()))));
            case LOAD_PACKAGE -> probe.sendTwice(Topics.WAREHOUSE_EVENTS, seen.key(), envelope("gw-loaded-" + id,
                    e -> e.setPackageLoaded(PackageLoaded.newBuilder().setOrderId(request.getLoadPackage().getOrderId()))));
            case REQUEST_PICKUP -> {
                long orderId = request.getRequestPickup().getOrderId();
                probe.sendTwice(Topics.CARRIER_EVENTS, seen.key(), envelope("ca-truck-" + id,
                        e -> e.setTruckArrived(TruckArrived.newBuilder().setOrderId(orderId).setTruckId(REAL_TRUCK))));
                // Not a redelivery: a genuinely different message claiming another truck.
                probe.send(Topics.CARRIER_EVENTS, seen.key(), envelope("ca-truck-again-" + id,
                        e -> e.setTruckArrived(TruckArrived.newBuilder().setOrderId(orderId).setTruckId(IMPOSTOR_TRUCK))));
            }
            case CONFIRM_LOADED -> {
                long orderId = request.getConfirmLoaded().getOrderId();
                probe.send(Topics.CARRIER_EVENTS, seen.key(), envelope("ca-out-" + id,
                        e -> e.setOutForDelivery(OutForDelivery.newBuilder().setOrderId(orderId))));
                probe.sendTwice(Topics.CARRIER_EVENTS, seen.key(), envelope("ca-delivered-" + id,
                        e -> e.setDelivered(Delivered.newBuilder().setOrderId(orderId))));
                probe.send(Topics.CARRIER_EVENTS, seen.key(), envelope("ca-delivered-again-" + id,
                        e -> e.setDelivered(Delivered.newBuilder().setOrderId(orderId))));
            }
            default -> {
                // an event, or nothing we answer
            }
        }
    }

    @Test
    void ordersAreFulfilledOnce_thoughEveryReplyArrivesTwice() {
        int perProduct = 50;
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Product product = fixtures.product("10.00", 0);
            replenishment.purchase(1, Long.parseLong(product.getProductId()), product.getName(), perProduct);
            products.add(product);
        }
        // The arrival was delivered twice; the stock must be counted once.
        await("stock to arrive", () -> products.stream().allMatch(p -> fixtures.stockOf(p) >= perProduct));
        assertThat(products).allSatisfy(p -> assertThat(fixtures.stockOf(p)).isEqualTo(perProduct));

        Random random = new Random(5);
        User buyer = fixtures.user();
        Map<String, Integer> sold = new HashMap<>();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            Product product = products.get(random.nextInt(products.size()));
            int quantity = 1 + random.nextInt(2);
            Order order = fixtures.confirmedOrder(buyer, product, quantity);
            sold.merge(product.getProductId(), quantity, Integer::sum);
            paymentService.processPayment(order.getId(), orderService.getOrder(order.getId()).getTotalPrice());
            orders.add(order);
        }

        await("every order to be delivered",
                () -> orders.stream().allMatch(o -> orderService.getOrder(o.getId()).getStatus() == Status.DELIVERED));

        for (Order order : orders) {
            long id = order.getId();
            assertThat(probe.distinct(Topics.WAREHOUSE_COMMANDS, e -> e.hasPackOrder() && e.getPackOrder().getOrderId() == id))
                    .as("pack requests for order %s", id).isEqualTo(1);
            assertThat(probe.distinct(Topics.CARRIER_COMMANDS, e -> e.hasRequestPickup() && e.getRequestPickup().getOrderId() == id))
                    .as("pickup requests for order %s", id).isEqualTo(1);
            assertThat(probe.distinct(Topics.WAREHOUSE_COMMANDS, e -> e.hasLoadPackage() && e.getLoadPackage().getOrderId() == id))
                    .as("load requests for order %s", id).isEqualTo(1);
            assertThat(probe.distinct(Topics.CARRIER_COMMANDS, e -> e.hasConfirmLoaded() && e.getConfirmLoaded().getOrderId() == id))
                    .as("load confirmations for order %s", id).isEqualTo(1);
            assertThat(orderService.getOrder(id).getTruckId()).as("truck of order %s", id).isEqualTo(REAL_TRUCK);
        }
        for (Product product : products) {
            assertThat(fixtures.stockOf(product)).isEqualTo(perProduct - sold.getOrDefault(product.getProductId(), 0));
        }
    }

    @Test
    void aNewWorld_evenWithTheSameId_putsAllStockBackOnOrder() {
        Product product = fixtures.product("2.00", 0);
        long productId = Long.parseLong(product.getProductId());
        replenishment.purchase(1, productId, product.getName(), 30);
        await("the first delivery of stock", () -> fixtures.stockOf(product) == 30);
        long generation = worldSession.generation();

        // The gateway announces world 1 again, as a later generation: the warehouse is empty.
        probe.send(Topics.WAREHOUSE_EVENTS, "world", envelope("gw-world-gen-" + (generation + 1), e -> e.setWorldAdopted(
                edu.ece568.MiniAmazon.proto.Events.WorldAdopted.newBuilder().setWorldId(1).setGeneration(generation + 1))));

        // Everything we held is bought again (the test's gateway delivers it), exactly once.
        await("the stock to be re-ordered", () -> purchasesOf(productId) == 2);
        await("the re-ordered stock to arrive", () -> fixtures.stockOf(product) == 30);

        // The same announcement again, as a different message: already followed, nothing happens.
        probe.send(Topics.WAREHOUSE_EVENTS, "world", envelope("gw-world-gen-repeat-" + (generation + 1), e -> e.setWorldAdopted(
                edu.ece568.MiniAmazon.proto.Events.WorldAdopted.newBuilder().setWorldId(1).setGeneration(generation + 1))));
        probe.send(Topics.WAREHOUSE_EVENTS, "product-" + productId, envelope("test-marker-" + productId, e -> e.setStockArrived(
                StockArrived.newBuilder().setWarehouseId(1).addItems(edu.ece568.MiniAmazon.proto.Events.Item.newBuilder()
                        .setProductId(productId).setDescription("marker").setCount(1)))));
        await("a later record to be applied", () -> fixtures.stockOf(product) == 31);
        assertThat(purchasesOf(productId)).as("purchase orders for the product").isEqualTo(2);
    }

    private long purchasesOf(long productId) {
        return probe.distinct(Topics.WAREHOUSE_COMMANDS, e -> e.hasPurchaseStock()
                && e.getPurchaseStock().getItemsList().stream().anyMatch(i -> i.getProductId() == productId));
    }

    @Test
    void anUnreadableRecordIsDeadLettered_andTheOnesBehindItStillGetThrough() {
        Product product = fixtures.product("1.00", 0);
        long productId = Long.parseLong(product.getProductId());
        String key = "product-" + productId; // same key, same partition: the good record is queued behind the bad one
        byte[] garbage = {(byte) 0xff, (byte) 0xff, (byte) 0xff, 0x01, 0x02};

        probe.sendRaw(Topics.WAREHOUSE_EVENTS, key, garbage);
        probe.send(Topics.WAREHOUSE_EVENTS, key, envelope("test-after-poison-" + productId,
                e -> e.setStockArrived(StockArrived.newBuilder().setWarehouseId(1)
                        .addItems(edu.ece568.MiniAmazon.proto.Events.Item.newBuilder()
                                .setProductId(productId).setDescription("after poison").setCount(9)))));

        await("the record behind the poison to be applied", () -> fixtures.stockOf(product) == 9);
        await("the poison to reach the dead-letter topic", () -> probe.on(Topics.WAREHOUSE_EVENTS + Topics.DEAD_LETTER_SUFFIX)
                .stream().anyMatch(s -> java.util.Arrays.equals(s.raw(), garbage)));
    }
}
