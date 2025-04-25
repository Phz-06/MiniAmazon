package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.service.WorldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/world")
@Slf4j
public class WorldController {

    private final WorldService worldService;

    @Autowired
    public WorldController(WorldService worldService) {
        this.worldService = worldService;
    }
    
    /**
     * Connect to the world simulator
     * 
     * @param worldId Optional ID of an existing world
     * @return Connection status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(
            @RequestParam(required = false) Long worldId,
            @RequestParam(defaultValue = "localhost") String host,
            @RequestParam(defaultValue = "23456") int port) {
        
        Map<String, Object> response = new HashMap<>();
        
        boolean connected = worldService.connect(host, port, worldId);
        
        if (connected) {
            response.put("status", "connected");
            response.put("worldId", worldService.getWorldId());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "failed");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Disconnect from the world simulator
     * 
     * @return Disconnection status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect() {
        Map<String, Object> response = new HashMap<>();
        
        if (!worldService.isConnected()) {
            response.put("status", "not connected");
            return ResponseEntity.badRequest().body(response);
        }
        
        worldService.disconnect();
        response.put("status", "disconnected");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Set the simulation speed
     * 
     * @param speed The simulation speed
     * @return Operation status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/speed")
    public ResponseEntity<Map<String, Object>> setSpeed(@RequestParam int speed) {
        Map<String, Object> response = new HashMap<>();
        
        if (!worldService.isConnected()) {
            response.put("status", "not connected");
            return ResponseEntity.badRequest().body(response);
        }
        
        worldService.setSimulationSpeed(speed);
        response.put("status", "success");
        response.put("speed", speed);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Buy a product
     * 
     * @param warehouseId The ID of the warehouse
     * @param productId The ID of the product
     * @param description The description of the product
     * @param count The count to buy
     * @return Operation status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/buy")
    public ResponseEntity<Map<String, Object>> buyProduct(
            @RequestParam int warehouseId,
            @RequestParam long productId,
            @RequestParam String description,
            @RequestParam int count) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (!worldService.isConnected()) {
            response.put("status", "not connected");
            return ResponseEntity.badRequest().body(response);
        }
        
        long seqNum = worldService.buyProduct(warehouseId, productId, description, count);
        
        if (seqNum > 0) {
            response.put("status", "success");
            response.put("seqNum", seqNum);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "failed");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Pack an order
     * 
     * @param warehouseId The ID of the warehouse
     * @param shipmentId The ID of the shipment
     * @param products The list of products in format [[id, count, description], ...]
     * @return Operation status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/pack")
    public ResponseEntity<Map<String, Object>> packOrder(
            @RequestParam int warehouseId,
            @RequestParam long shipmentId,
            @RequestBody List<Object[]> products) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (!worldService.isConnected()) {
            response.put("status", "not connected");
            return ResponseEntity.badRequest().body(response);
        }
        
        long seqNum = worldService.packOrder(warehouseId, shipmentId, products);
        
        if (seqNum > 0) {
            response.put("status", "success");
            response.put("seqNum", seqNum);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "failed");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Load a package onto a truck
     * 
     * @param warehouseId The ID of the warehouse
     * @param truckId The ID of the truck
     * @param shipmentId The ID of the shipment
     * @return Operation status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/load")
    public ResponseEntity<Map<String, Object>> loadPackage(
            @RequestParam int warehouseId,
            @RequestParam int truckId,
            @RequestParam long shipmentId) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (!worldService.isConnected()) {
            response.put("status", "not connected");
            return ResponseEntity.badRequest().body(response);
        }
        
        long seqNum = worldService.loadPackage(warehouseId, truckId, shipmentId);
        
        if (seqNum > 0) {
            response.put("status", "success");
            response.put("seqNum", seqNum);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "failed");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Query package status
     * 
     * @param packageId The ID of the package
     * @return Operation status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/package/{packageId}")
    public ResponseEntity<Map<String, Object>> queryPackage(@PathVariable long packageId) {
        Map<String, Object> response = new HashMap<>();
        
        if (!worldService.isConnected()) {
            response.put("status", "not connected");
            return ResponseEntity.badRequest().body(response);
        }
        
        long seqNum = worldService.queryPackage(packageId);
        
        if (seqNum > 0) {
            response.put("status", "success");
            response.put("seqNum", seqNum);
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "failed");
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get world ID
     * 
     * @return The world ID
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/id")
    public ResponseEntity<Map<String, Object>> getWorldId() {
        Map<String, Object> response = new HashMap<>();
        
        if (!worldService.isConnected()) {
            response.put("status", "not connected");
            return ResponseEntity.badRequest().body(response);
        }
        
        response.put("status", "success");
        response.put("worldId", worldService.getWorldId());
        return ResponseEntity.ok(response);
    }
} 