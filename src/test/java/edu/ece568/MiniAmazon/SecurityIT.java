package edu.ece568.MiniAmazon;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ece568.MiniAmazon.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SecurityIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void catalogIsPublic() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void ordersNeedALogin() throws Exception {
        int status = mvc.perform(get("/api/orders/1")).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void worldAndUpsControls_areAdminOnly() throws Exception {
        for (String path : new String[]{"/api/world/id", "/api/admin/ups/status"}) {
            int anonymous = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(anonymous).as("anonymous %s", path).isIn(401, 403);

            mvc.perform(get(path).header("Authorization", "Bearer " + signUp()))
                    .andExpect(status().isForbidden());

            int admin = mvc.perform(get(path).header("Authorization", "Bearer " + login("admin", "admin-test-password")))
                    .andReturn().getResponse().getStatus();
            assertThat(admin).as("admin %s", path).isNotIn(401, 403);
        }
    }

    @Test
    void wrongPassword_isUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "admin", "password", "admin"))))
                .andExpect(status().isUnauthorized());
    }

    private String signUp() throws Exception {
        String name = "s" + (System.nanoTime() % 100_000_000);
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", name, "password", "password123", "email", name + "@example.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }
}
