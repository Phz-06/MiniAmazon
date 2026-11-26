package edu.ece568.MiniAmazon.support;

import edu.ece568.MiniAmazon.dto.request.OrderItemRequest;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.model.UserRole;
import edu.ece568.MiniAmazon.model.WareHouse;
import edu.ece568.MiniAmazon.repository.ProductRepository;
import edu.ece568.MiniAmazon.repository.UserRepository;
import edu.ece568.MiniAmazon.repository.WareHouseRepository;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.ProductService;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Test data with unique names, so tests can share one database without cleaning up after each other. */
@TestComponent
public class Fixtures {
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    private final UserRepository users;
    private final WareHouseRepository warehouses;
    private final ProductRepository products;
    private final ProductService productService;
    private final OrderService orderService;

    public Fixtures(UserRepository users, WareHouseRepository warehouses, ProductRepository products,
                    ProductService productService, OrderService orderService) {
        this.users = users;
        this.warehouses = warehouses;
        this.products = products;
        this.productService = productService;
        this.orderService = orderService;
    }

    public User user() {
        long n = SEQ.incrementAndGet();
        User user = new User();
        user.setUsername("u" + n);
        user.setEmail("u" + n + "@example.com");
        user.setPassword("{noop}unused");
        user.setRole(UserRole.USER);
        user.setAccountEnabled(true);
        return users.save(user);
    }

    public Product product(String price, int stock) {
        WareHouse warehouse = new WareHouse();
        warehouse.setName("test");
        warehouse = warehouses.save(warehouse);
        String productId = String.valueOf(SEQ.incrementAndGet());
        return productService.createProduct("Product " + productId, productId, new BigDecimal(price),
                "test product", stock, "Test", warehouse.getId());
    }

    public int stockOf(Product product) {
        return products.findByProductId(product.getProductId()).getQuantity();
    }

    /** A CREATED order; lines are (product, quantity) in the given order. */
    public Order order(User user, Object... productThenQuantity) {
        List<OrderItemRequest> lines = new ArrayList<>();
        for (int i = 0; i < productThenQuantity.length; i += 2) {
            OrderItemRequest line = new OrderItemRequest();
            line.setProductId(((Product) productThenQuantity[i]).getProductId());
            line.setQuantity((Integer) productThenQuantity[i + 1]);
            lines.add(line);
        }
        return orderService.createOrder(user.getId(), lines);
    }

    public Order confirmedOrder(User user, Object... productThenQuantity) {
        Order order = order(user, productThenQuantity);
        orderService.confirmOrder(order.getId(), 1, 1);
        return order;
    }
}
