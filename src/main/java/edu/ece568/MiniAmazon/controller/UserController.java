package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/my-orders")
    public ResponseEntity<List<Order>> getMyOrders(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("Received request to get orders for user ID: {}", userDetails.getUsername());
        Long userId = userService.getUserByUsername(userDetails.getUsername());
        List<Order> orders = userService.getAllOrders(userId);
        return ResponseEntity.ok(orders);
    }
}
