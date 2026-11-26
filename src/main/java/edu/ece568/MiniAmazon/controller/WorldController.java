package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.fulfillment.ReplenishmentService;
import edu.ece568.MiniAmazon.messaging.Destination;
import edu.ece568.MiniAmazon.messaging.Outbox;
import edu.ece568.MiniAmazon.world.WorldGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Operator controls for the world connection. Packing and loading are driven by orders, not from here. */
@Slf4j
@RestController
@RequestMapping("/api/world")
@PreAuthorize("hasRole('ADMIN')")
public class WorldController {

    private final WorldGateway gateway;
    private final ReplenishmentService replenishment;
    private final Outbox outbox;

    public WorldController(WorldGateway gateway, ReplenishmentService replenishment, Outbox outbox) {
        this.gateway = gateway;
        this.replenishment = replenishment;
        this.outbox = outbox;
    }

    /**
     * Without parameters: rejoin our world, or create one if we never had one.
     * worldId joins a specific world; fresh=true always creates a new one.
     * Moving to a different world puts all stock back on order.
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestParam(required = false) Long worldId,
                                                       @RequestParam(defaultValue = "false") boolean fresh) {
        try {
            long connected = gateway.connect(worldId, fresh);
            return ResponseEntity.ok(Map.of("status", "connected", "worldId", connected));
        } catch (IOException e) {
            log.warn("World connect failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("status", "failed", "message", String.valueOf(e.getMessage())));
        }
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect() {
        gateway.disconnect();
        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }

    @PostMapping("/speed")
    public ResponseEntity<Map<String, Object>> setSimSpeed(@RequestParam int speed) {
        try {
            gateway.setSimulationSpeed(speed);
            return ResponseEntity.ok(Map.of("status", "success", "speed", speed));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "not connected"));
        }
    }

    /** Orders stock. It is queued even while disconnected and becomes sellable when the world reports it arrived. */
    @PostMapping("/buy")
    public ResponseEntity<Map<String, Object>> buyProduct(
            @RequestParam(defaultValue = "1") int warehouseId,
            @RequestParam long productId,
            @RequestParam String description,
            @RequestParam int count) {
        long seqNum = replenishment.purchase(warehouseId, productId, description, count);
        return ResponseEntity.ok(Map.of("status", "success", "seqNum", seqNum));
    }

    @GetMapping("/id")
    public ResponseEntity<Map<String, Object>> getWorldId() {
        Long worldId = gateway.worldId();
        if (worldId == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "not connected"));
        }
        return ResponseEntity.ok(Map.of("status", "success", "worldId", worldId));
    }

    /** Connection state and the size of the command backlog. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", gateway.isConnected());
        body.put("worldId", gateway.worldId());
        body.put("pendingCommands", outbox.count(Destination.WORLD, "PENDING"));
        body.put("failedCommands", outbox.count(Destination.WORLD, "FAILED"));
        body.put("pendingCarrierMessages", outbox.count(Destination.CARRIER, "PENDING"));
        return ResponseEntity.ok(body);
    }
}
