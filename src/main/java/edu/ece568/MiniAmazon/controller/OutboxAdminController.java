package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.messaging.Outbox;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The dead-letter view. A message lands here when the other side refused it,
 * which no amount of automatic retrying fixes. An operator looks at why and
 * decides whether to send it again.
 */
@RestController
@RequestMapping("/api/admin/outbox")
@PreAuthorize("hasRole('ADMIN')")
public class OutboxAdminController {

    private final Outbox outbox;

    public OutboxAdminController(Outbox outbox) {
        this.outbox = outbox;
    }

    @GetMapping("/failed")
    public List<Map<String, Object>> failed() {
        return outbox.listFailed(100);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable long id) {
        if (!outbox.retry(id)) {
            return ResponseEntity.status(409).body(Map.of("error", "conflict", "message", "Message " + id + " is not in FAILED state"));
        }
        return ResponseEntity.accepted().body(Map.of("id", id, "status", "PENDING"));
    }
}
