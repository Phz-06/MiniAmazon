package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.exception.InsufficientStockException;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.OrderItem;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.Status;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.service.InventoryService;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.PaymentService;
import edu.ece568.MiniAmazon.service.ProductService;
import edu.ece568.MiniAmazon.support.Fixtures;
import edu.ece568.MiniAmazon.support.IntegrationTest;
import edu.ece568.MiniAmazon.support.Race;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What happens when many requests hit the same rows at once. Each test states
 * an invariant that must hold no matter how the threads interleave.
 */
@IntegrationTest
class InventoryConcurrencyIT {

    @Autowired Fixtures fixtures;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired ProductService productService;
    @Autowired InventoryService inventoryService;
    @Autowired TransactionTemplate tx;

    @Test
    void sixtyBuyersForTwentyUnits_exactlyTwentyWin_andStockEndsAtZero() throws Exception {
        Product hot = fixtures.product("9.99", 20);
        User buyer = fixtures.user();
        List<Callable<?>> buyers = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Order order = fixtures.order(buyer, hot, 1);
            buyers.add(() -> {
                orderService.confirmOrder(order.getId(), 1, 1);
                return null;
            });
        }

        Race.Result result = Race.run(buyers);

        assertThat(result.succeeded()).isEqualTo(20);
        assertThat(result.failuresOf(InsufficientStockException.class)).isEqualTo(40);
        assertThat(fixtures.stockOf(hot)).isZero();
    }

    @Test
    void ordersLockingTheSameProductsInOppositeOrder_neverDeadlock() throws Exception {
        Product a = fixtures.product("1.00", 10_000);
        Product b = fixtures.product("1.00", 10_000);
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            List<OrderItem> lines = i % 2 == 0 ? List.of(line(a, 1), line(b, 1)) : List.of(line(b, 1), line(a, 1));
            tasks.add(() -> {
                for (int round = 0; round < 25; round++) {
                    tx.executeWithoutResult(status -> inventoryService.lockInventory(lines));
                }
                return null;
            });
        }

        Race.Result result = Race.run(tasks);

        assertThat(result.failures()).isEmpty();
        assertThat(fixtures.stockOf(a)).isEqualTo(10_000 - 40 * 25);
        assertThat(fixtures.stockOf(b)).isEqualTo(10_000 - 40 * 25);
    }

    @Test
    void anOrderShortOnOneLine_reservesNothingAtAll() {
        Product plenty = fixtures.product("1.00", 10);
        Product scarce = fixtures.product("1.00", 1);
        Order order = fixtures.order(fixtures.user(), plenty, 5, scarce, 2);

        try {
            orderService.confirmOrder(order.getId(), 1, 1);
        } catch (InsufficientStockException expected) {
            // fall through to the assertions
        }

        assertThat(fixtures.stockOf(plenty)).isEqualTo(10);
        assertThat(fixtures.stockOf(scarce)).isEqualTo(1);
        assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(Status.CREATED);
    }

    @Test
    void theSamePaymentSubmittedEightTimes_isAcceptedOnce() throws Exception {
        Product product = fixtures.product("12.50", 10);
        Order order = fixtures.confirmedOrder(fixtures.user(), product, 2);
        List<Callable<?>> submissions = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            submissions.add(() -> {
                paymentService.processPayment(order.getId(), new BigDecimal("25.00"));
                return null;
            });
        }

        Race.Result result = Race.run(submissions);

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failuresOf(IllegalStateException.class)).isEqualTo(7);
    }

    @Test
    void cancelRacingWithPayment_oneWins_andStockIsReturnedOnlyIfCancelWon() throws Exception {
        for (int round = 0; round < 20; round++) {
            Product product = fixtures.product("5.00", 10);
            Order order = fixtures.confirmedOrder(fixtures.user(), product, 4);

            Race.Result result = Race.run(List.of(
                    () -> {
                        orderService.cancelOrder(order.getId());
                        return null;
                    },
                    () -> {
                        paymentService.processPayment(order.getId(), new BigDecimal("20.00"));
                        return null;
                    }));

            assertThat(result.succeeded()).isEqualTo(1);
            Status status = orderService.getOrder(order.getId()).getStatus();
            assertThat(status).isIn(Status.CANCELED, Status.PAID);
            assertThat(fixtures.stockOf(product)).isEqualTo(status == Status.CANCELED ? 10 : 6);
        }
    }

    @Test
    void tenCancelsOfOneOrder_returnItsStockOnce() throws Exception {
        Product product = fixtures.product("5.00", 10);
        Order order = fixtures.confirmedOrder(fixtures.user(), product, 4);
        List<Callable<?>> cancels = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cancels.add(() -> {
                orderService.cancelOrder(order.getId());
                return null;
            });
        }

        Race.Result result = Race.run(cancels);

        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(fixtures.stockOf(product)).isEqualTo(10);
    }

    @Test
    void priceEditsAndRestocksDuringPurchases_doNotEraseAnyStockMovement() throws Exception {
        Product product = fixtures.product("10.00", 100);
        User buyer = fixtures.user();
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Order order = fixtures.order(buyer, product, 1);
            tasks.add(() -> {
                orderService.confirmOrder(order.getId(), 1, 1);
                return null;
            });
        }
        for (int i = 0; i < 20; i++) {
            BigDecimal price = new BigDecimal("10.00").add(BigDecimal.valueOf(i));
            tasks.add(() -> productService.updateProductPriceAndCategory(product.getId(), price, "Test"));
        }
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                productService.increaseInventory(Long.parseLong(product.getProductId()), "restock", 3, 1);
                return null;
            });
        }

        Race.Result result = Race.run(tasks);

        assertThat(result.failures()).isEmpty();
        assertThat(fixtures.stockOf(product)).isEqualTo(100 - 50 + 20 * 3);
    }

    private static OrderItem line(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }
}
