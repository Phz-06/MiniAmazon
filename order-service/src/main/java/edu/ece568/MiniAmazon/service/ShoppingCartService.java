package edu.ece568.MiniAmazon.service;

import edu.ece568.MiniAmazon.dto.request.OrderItemRequest;
import edu.ece568.MiniAmazon.model.CartItem;
import edu.ece568.MiniAmazon.model.Order;
import edu.ece568.MiniAmazon.model.Product;
import edu.ece568.MiniAmazon.model.ShoppingCart;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.repository.CartItemRepository;
import edu.ece568.MiniAmazon.repository.ShoppingCartRepository;
import edu.ece568.MiniAmazon.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ShoppingCartService {

    private final ShoppingCartRepository shoppingCartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final OrderService orderService;

    public ShoppingCartService(ShoppingCartRepository shoppingCartRepository, 
                               CartItemRepository cartItemRepository,
                               UserRepository userRepository,
                               ProductService productService,
                               OrderService orderService) {
        this.shoppingCartRepository = shoppingCartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productService = productService;
        this.orderService = orderService;
    }

    /**
     * Convert the shopping cart to an order
     */
    @Transactional
    public Order createOrderFromCart(Long userId, int deliveryX, int deliveryY, String upsAccountName) {
        ShoppingCart cart = getOrCreateCart(userId);
        
        if (cart.getCartItems().isEmpty()) {
            throw new IllegalStateException("Cannot create order from empty cart");
        }
        
        // Convert cart items to order items
        List<OrderItemRequest> orderItems = new ArrayList<>();
        for (CartItem item : cart.getCartItems()) {
            OrderItemRequest orderItem = new OrderItemRequest();
            orderItem.setProductId(item.getProduct().getProductId());
            orderItem.setQuantity(item.getQuantity());
            orderItems.add(orderItem);
        }
        
        // Create the order
        Order order = orderService.createOrder(userId, orderItems);
        order.setDeliveryX(deliveryX);
        order.setDeliveryY(deliveryY);
        
        if (upsAccountName != null && !upsAccountName.isEmpty()) {
            order.setUpsAccountName(upsAccountName);
        }
        
        // Clear the cart after successful order creation
        clearCart(userId);
        
        return order;
    }

    /**
     * Get a user's shopping cart, creating it if it doesn't exist
     */
    @Transactional
    public ShoppingCart getOrCreateCart(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User with ID " + userId + " not found"));
        
        return shoppingCartRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    log.info("Creating new shopping cart for user ID: {}", userId);
                    ShoppingCart cart = new ShoppingCart();
                    cart.setUser(user);
                    return shoppingCartRepository.save(cart);
                });
    }

    /**
     * Add a product to the cart
     */
    @Transactional
    public ShoppingCart addProductToCart(Long userId, String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        
        ShoppingCart cart = getOrCreateCart(userId);
        Product product = productService.getProductById(productId);
        
        if (product == null) {
            throw new IllegalArgumentException("Product with ID " + productId + " not found");
        }
        
        if (product.getQuantity() < quantity) {
            throw new IllegalArgumentException("Not enough product in stock. Available: " + product.getQuantity());
        }
        
        Optional<CartItem> existingItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getProductId().equals(productId))
                .findFirst();
        
        if (existingItem.isPresent()) {
            // Update existing item
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            item.setPrice(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            cartItemRepository.save(item);
        } else {
            // Create new item
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setPrice(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
            cart.getCartItems().add(item);
            cartItemRepository.save(item);
        }
        
        // Update cart totals
        updateCartTotals(cart);
        
        return shoppingCartRepository.save(cart);
    }
    
    /**
     * Update product quantity in cart
     */
    @Transactional
    public ShoppingCart updateCartItem(Long userId, String productId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        
        ShoppingCart cart = getOrCreateCart(userId);
        
        Optional<CartItem> existingItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getProductId().equals(productId))
                .findFirst();
        
        if (existingItem.isEmpty()) {
            throw new IllegalArgumentException("Product not in cart");
        }
        
        CartItem item = existingItem.get();
        
        if (quantity == 0) {
            // Remove item if quantity is 0
            cart.getCartItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            Product product = item.getProduct();
            
            if (product.getQuantity() < quantity) {
                throw new IllegalArgumentException("Not enough product in stock. Available: " + product.getQuantity());
            }
            
            item.setQuantity(quantity);
            item.setPrice(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
            cartItemRepository.save(item);
        }
        
        // Update cart totals
        updateCartTotals(cart);
        
        return shoppingCartRepository.save(cart);
    }
    
    /**
     * Remove a product from the cart
     */
    @Transactional
    public ShoppingCart removeProductFromCart(Long userId, String productId) {
        ShoppingCart cart = getOrCreateCart(userId);
        
        Optional<CartItem> existingItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().getProductId().equals(productId))
                .findFirst();
        
        if (existingItem.isEmpty()) {
            throw new IllegalArgumentException("Product not in cart");
        }
        
        CartItem item = existingItem.get();
        cart.getCartItems().remove(item);
        cartItemRepository.delete(item);
        
        // Update cart totals
        updateCartTotals(cart);
        
        return shoppingCartRepository.save(cart);
    }
    
    /**
     * Clear the cart
     */
    @Transactional
    public ShoppingCart clearCart(Long userId) {
        ShoppingCart cart = getOrCreateCart(userId);
        
        cart.getCartItems().clear();
        cartItemRepository.deleteAll(cart.getCartItems());
        
        cart.setTotalPrice(BigDecimal.ZERO);
        cart.setTotalQuantity(0);
        
        return shoppingCartRepository.save(cart);
    }
    
    /**
     * Update cart totals (price and quantity)
     */
    private void updateCartTotals(ShoppingCart cart) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        int totalQuantity = 0;
        
        for (CartItem item : cart.getCartItems()) {
            totalPrice = totalPrice.add(item.getPrice());
            totalQuantity += item.getQuantity();
        }
        
        cart.setTotalPrice(totalPrice);
        cart.setTotalQuantity(totalQuantity);
    }
} 