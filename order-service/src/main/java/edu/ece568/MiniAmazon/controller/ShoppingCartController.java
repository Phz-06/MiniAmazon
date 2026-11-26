package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.dto.request.AddToCartRequest;
import edu.ece568.MiniAmazon.dto.request.CreateOrderFromCartRequest;
import edu.ece568.MiniAmazon.dto.request.UpdateCartItemRequest;
import edu.ece568.MiniAmazon.dto.response.OrderDTO;
import edu.ece568.MiniAmazon.dto.response.ShoppingCartDTO;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.ShoppingCart;
import edu.ece568.MiniAmazon.service.ShoppingCartService;
import edu.ece568.MiniAmazon.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cart")
public class ShoppingCartController {
    private final ShoppingCartService cartService;
    private final UserService userService;

    public ShoppingCartController(ShoppingCartService cartService, UserService userService) {
        this.cartService = cartService;
        this.userService = userService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<ShoppingCartDTO> getCart(@AuthenticationPrincipal UserDetails user) {
        Long userId = userService.getUserIdByUsername(user.getUsername());
        ShoppingCart cart = cartService.getOrCreateCart(userId);
        return ResponseEntity.ok(ShoppingCartDTO.fromEntity(cart));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/add")
    public ResponseEntity<ShoppingCartDTO> addToCart(
            @Valid @RequestBody AddToCartRequest request,
            @AuthenticationPrincipal UserDetails user) {
        
        Long userId = userService.getUserIdByUsername(user.getUsername());
        ShoppingCart cart = cartService.addProductToCart(
                userId, 
                request.getProductId(), 
                request.getQuantity());
        
        return ResponseEntity.ok(ShoppingCartDTO.fromEntity(cart));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/update")
    public ResponseEntity<ShoppingCartDTO> updateCartItem(
            @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal UserDetails user) {
        
        Long userId = userService.getUserIdByUsername(user.getUsername());
        ShoppingCart cart = cartService.updateCartItem(
                userId, 
                request.getProductId(), 
                request.getQuantity());
        
        return ResponseEntity.ok(ShoppingCartDTO.fromEntity(cart));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<ShoppingCartDTO> removeFromCart(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetails user) {
        
        Long userId = userService.getUserIdByUsername(user.getUsername());
        ShoppingCart cart = cartService.removeProductFromCart(userId, productId);
        
        return ResponseEntity.ok(ShoppingCartDTO.fromEntity(cart));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/clear")
    public ResponseEntity<ShoppingCartDTO> clearCart(@AuthenticationPrincipal UserDetails user) {
        Long userId = userService.getUserIdByUsername(user.getUsername());
        ShoppingCart cart = cartService.clearCart(userId);
        
        return ResponseEntity.ok(ShoppingCartDTO.fromEntity(cart));
    }
    
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(
            @Valid @RequestBody CreateOrderFromCartRequest request,
            @AuthenticationPrincipal UserDetails user) {
        
        log.info("Processing checkout for user: {}", user.getUsername());
        
        Long userId = userService.getUserIdByUsername(user.getUsername());
        
        Order order = cartService.createOrderFromCart(
                userId,
                request.getDeliveryX(),
                request.getDeliveryY(),
                request.getUpsAccountName());
        
        log.info("Created order {} from cart checkout for user {}", order.getId(), user.getUsername());
        
        Map<String, String> orderDetails = Map.of(
                "orderId", String.valueOf(order.getId()),
                "totalPrice", order.getTotalPrice().toPlainString(),
                "status", "success"
        );
        
        return ResponseEntity.accepted().body(orderDetails);
    }
} 