package edu.ece568.MiniAmazon.security;

import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.repository.OrderRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("orderSecurity")
public class OrderSecurityEvaluator {

    private final OrderRepository orderRepository;

    public OrderSecurityEvaluator(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public boolean isOrderOwner(Long orderId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;

        // Get username of authenticated user
        String username = authentication.getName();

        // Find order by ID
        return orderRepository.findById(orderId)
                .map(order -> order.getUser().getUsername().equals(username))
                .orElse(false);
    }

    public boolean isOrderOwnerOrAdmin(Long orderId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;

        // Check for admin role
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) return true;

        // If not admin, check if owner
        return isOrderOwner(orderId);
    }
}