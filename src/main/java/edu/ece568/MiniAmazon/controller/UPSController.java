package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.config.UPSConfiguration;
import edu.ece568.MiniAmazon.dto.request.UPSConnectionRequest;
import edu.ece568.MiniAmazon.dto.request.UPSServerRequest;
import edu.ece568.MiniAmazon.service.UPSService;
import edu.ece568.MiniAmazon.service.UPSSocketServerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/ups")
public class UPSController {
    
    private final UPSService upsService;
    private final UPSConfiguration upsConfiguration;
    private final UPSSocketServerService upsSocketServerService;
    
    public UPSController(UPSService upsService, UPSConfiguration upsConfiguration, UPSSocketServerService upsSocketServerService) {
        this.upsService = upsService;
        this.upsConfiguration = upsConfiguration;
        this.upsSocketServerService = upsSocketServerService;
    }
    
    /**
     * Connect to the UPS server with default values
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/connect/default")
    public ResponseEntity<Map<String, Object>> connectToUPSWithDefaults() {
        log.info("Connecting to UPS server with default settings");
        
        boolean success = upsConfiguration.connectToUPS();
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "connected",
                "message", "Successfully connected to UPS server"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "failed",
                "message", "Failed to connect to UPS server"
            ));
        }
    }
    
    /**
     * Connect to the UPS server with custom values
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connectToUPS(@Valid @RequestBody UPSConnectionRequest request) {
        log.info("Received request to connect to UPS server at {}:{}", request.getHost(), request.getPort());
        
        boolean success = upsConfiguration.connectToUPS(request.getHost(), request.getPort());
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "connected",
                "message", "Successfully connected to UPS server"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "failed",
                "message", "Failed to connect to UPS server"
            ));
        }
    }
    
    /**
     * Disconnect from the UPS server
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectFromUPS() {
        log.info("Received request to disconnect from UPS server");
        
        upsConfiguration.disconnectFromUPS();
        
        return ResponseEntity.ok(Map.of(
            "status", "disconnected",
            "message", "Disconnected from UPS server"
        ));
    }
    
    /**
     * Get the connection status
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        boolean isConnected = upsService.isConnected();
        boolean isServerRunning = upsSocketServerService.getServerRunning().get();
        boolean isClientConnected = upsSocketServerService.isUpsClientConnected();
        
        return ResponseEntity.ok(Map.of(
            "connected", isConnected,
            "status", isConnected ? "connected" : "disconnected",
            "serverRunning", isServerRunning,
            "upsClientConnected", isClientConnected
        ));
    }
    
    /**
     * Start the UPS socket server with optional custom port
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/server/start")
    public ResponseEntity<Map<String, Object>> startUPSSocketServer(@RequestBody(required = false) UPSServerRequest request) {
        Integer port = (request != null) ? request.getPort() : null;
        
        log.info("Starting UPS socket server" + (port != null ? " on port " + port : ""));
        
        boolean success = upsSocketServerService.startServer(port);
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "UPS socket server started successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "failed",
                "message", "Failed to start UPS socket server"
            ));
        }
    }
    
    /**
     * Stop the UPS socket server
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/server/stop")
    public ResponseEntity<Map<String, Object>> stopUPSSocketServer() {
        log.info("Stopping UPS socket server");
        
        upsSocketServerService.stopServer();
        
        return ResponseEntity.ok(Map.of(
            "status", "stopped",
            "message", "UPS socket server stopped"
        ));
    }
}
