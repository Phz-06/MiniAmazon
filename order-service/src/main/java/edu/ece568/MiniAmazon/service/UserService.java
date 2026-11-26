package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import edu.ece568.MiniAmazon.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public UserService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Long getUserIdByUsername(String username) {
        return userRepository.findUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User with username " + username + " not found"))
                .getId();
    }

    @Transactional
    public Long getUserByUsername(String username) {
        return userRepository.findUserByUsername(username)
                .orElseThrow(() -> new RuntimeException("User with username " + username + " not found"))
                .getId();
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders(Long userId) {
        if (!userRepository.existsById(userId)) {
            log.error("User with ID {} not found", userId);
            throw new RuntimeException("User with ID " + userId + " not found");
        }
        log.info("Retrieving all orders for user with ID {}", userId);
        return orderRepository.findOrdersById((userId));
    }
}
