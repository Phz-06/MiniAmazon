package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.fulfillment.ReplenishmentService;
import edu.ece568.MiniAmazon.shared.messaging.EventOutbox;
import edu.ece568.MiniAmazon.shared.worldlink.WorldSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operator view of the warehouse. The connection itself belongs to the
 * world-gateway service, which joins or creates its world on its own.
 */
@RestController
@RequestMapping("/api/world")
@PreAuthorize("hasRole('ADMIN')")
public class WorldController {

    private final ReplenishmentService replenishment;
    private final EventOutbox outbox;
    private final WorldSession session;

    public WorldController(ReplenishmentService replenishment, EventOutbox outbox, WorldSession session) {
        this.replenishment = replenishment;
        this.outbox = outbox;
        this.session = session;
    }

    /** Orders stock. It becomes sellable when the warehouse reports it arrived. */
    @PostMapping("/buy")
    public ResponseEntity<Map<String, Object>> buyProduct(
            @RequestParam(defaultValue = "1") int warehouseId,
            @RequestParam long productId,
            @RequestParam String description,
            @RequestParam int count) {
        long seqNum = replenishment.purchase(warehouseId, productId, description, count);
        return ResponseEntity.ok(Map.of("status", "success", "seqNum", seqNum));
    }

    /** The world our stock figures describe, and how many messages are still waiting to reach Kafka. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("worldId", session.worldId());
        body.put("pendingMessages", outbox.pending());
        return ResponseEntity.ok(body);
    }
}
