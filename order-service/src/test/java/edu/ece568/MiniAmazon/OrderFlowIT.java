package edu.ece568.MiniAmazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.support.Fixtures;
import edu.ece568.MiniAmazon.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The customer journey over HTTP: sign up, fill the cart, check out, confirm, pay. */
@IntegrationTest
class OrderFlowIT {
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Fixtures fixtures;

    @Test
    void cartToPaidOrder() throws Exception {
        Product mouse = fixtures.product("19.99", 10);
        Product cable = fixtures.product("5.00", 10);
        String token = signUp();

        addToCart(token, mouse, 3);
        addToCart(token, cable, 1);
        call(get("/api/cart"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.cartItems.length()").value(2));

        long orderId = checkout(token);
        // 3 x 19.99 + 1 x 5.00. The old code summed unit prices and ignored quantity.
        call(get("/api/orders/" + orderId), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(64.97))
                .andExpect(jsonPath("$.status").value("CREATED"));

        // Stock is untouched until the order is confirmed.
        assertThat(fixtures.stockOf(mouse)).isEqualTo(10);
        confirm(token, orderId).andExpect(status().isAccepted());
        assertThat(fixtures.stockOf(mouse)).isEqualTo(7);
        assertThat(fixtures.stockOf(cable)).isEqualTo(9);
        confirm(token, orderId).andExpect(status().isConflict());
        assertThat(fixtures.stockOf(mouse)).isEqualTo(7);

        pay(token, orderId, "1.00").andExpect(status().isBadRequest());
        pay(token, orderId, "64.97").andExpect(status().isAccepted());
        pay(token, orderId, "64.97").andExpect(status().isConflict());

        // Paid orders are with the warehouse; they can no longer be canceled here.
        call(post("/api/orders/" + orderId + "/cancel"), token).andExpect(status().isConflict());
        assertThat(fixtures.stockOf(mouse)).isEqualTo(7);

        // Raising the catalog price afterwards does not rewrite what was charged.
        call(get("/api/orders/" + orderId), token)
                .andExpect(jsonPath("$.orderItems[?(@.quantity == 3)].price").value(19.99));
    }

    @Test
    void confirmingMoreThanIsInStock_isAConflict_andTheOrderStaysOpen() throws Exception {
        Product rare = fixtures.product("100.00", 2);
        String token = signUp();
        addToCart(token, rare, 2);
        long orderId = checkout(token);

        // Someone else takes one unit between checkout and confirmation.
        fixtures.confirmedOrder(fixtures.user(), rare, 1);

        confirm(token, orderId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("insufficient_stock"));
        call(get("/api/orders/" + orderId + "/status"), token)
                .andExpect(jsonPath("$.status").value("CREATED"));
        assertThat(fixtures.stockOf(rare)).isEqualTo(1);
    }

    @Test
    void cancelingAConfirmedOrder_returnsItsStock() throws Exception {
        Product product = fixtures.product("3.00", 5);
        String token = signUp();
        addToCart(token, product, 5);
        long orderId = checkout(token);
        confirm(token, orderId).andExpect(status().isAccepted());
        assertThat(fixtures.stockOf(product)).isZero();

        call(post("/api/orders/" + orderId + "/cancel"), token).andExpect(status().isAccepted());

        assertThat(fixtures.stockOf(product)).isEqualTo(5);
        call(get("/api/orders/" + orderId + "/status"), token)
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void anotherCustomersOrder_isForbidden() throws Exception {
        Product product = fixtures.product("3.00", 5);
        String owner = signUp();
        addToCart(owner, product, 1);
        long orderId = checkout(owner);

        String stranger = signUp();
        call(get("/api/orders/" + orderId), stranger).andExpect(status().isForbidden());
        pay(stranger, orderId, "3.00").andExpect(status().isForbidden());
    }

    private String signUp() throws Exception {
        String name = "c" + SEQ.incrementAndGet();
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", name, "password", "password123", "email", name + "@example.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private void addToCart(String token, Product product, int quantity) throws Exception {
        call(post("/api/cart/add"), token, Map.of("productId", product.getProductId(), "quantity", quantity))
                .andExpect(status().isOk());
    }

    private long checkout(String token) throws Exception {
        String body = call(post("/api/cart/checkout"), token, Map.of("deliveryX", 3, "deliveryY", 4))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("orderId").asLong();
    }

    private ResultActions confirm(String token, long orderId) throws Exception {
        return call(post("/api/orders/" + orderId + "/confirm"), token, Map.of("orderId", orderId, "x", 3, "y", 4));
    }

    private ResultActions pay(String token, long orderId, String amount) throws Exception {
        return call(post("/api/orders/" + orderId + "/payment"), token,
                Map.of("orderId", orderId, "creditCardNumber", "4111111111111111", "amount", amount));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
