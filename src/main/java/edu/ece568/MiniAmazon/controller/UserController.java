package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.dto.response.OrderDTO;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import edu.ece568.MiniAmazon.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/users")
public class UserController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public UserController(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/me/my-orders")
    public ResponseEntity<List<OrderDTO>> getMyOrders(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = authentication.getName();

        User user = userRepository.findUserByUsername(username)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<Order> orders = orderRepository.findOrdersById(user.getId());

        List<OrderDTO> orderDTOs = orders.stream()
                .map(OrderDTO::fromEntity)
                .toList();

        return ResponseEntity.ok(orderDTOs);
    }
}
