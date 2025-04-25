package edu.ece568.MiniAmazon.config;

import edu.ece568.MiniAmazon.service.UPSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
public class UPSConfiguration {

    private final UPSService upsService;

    @Value("${ups.server.host:localhost}")
    private String upsHost;

    @Value("${ups.server.port:8001}")
    private int upsPort;
    
    // Track if an admin has explicitly requested a connection
    private boolean connectionRequestedByAdmin = false;

    public UPSConfiguration(UPSService upsService) {
        this.upsService = upsService;
    }

    /**
     * Connect to the UPS server - This is now only called explicitly by admin
     */
    public boolean connectToUPS() {
        log.info("Connecting to UPS server at {}:{}", upsHost, upsPort);
        
        boolean connected = upsService.connect(upsHost, upsPort);
        
        if (connected) {
            log.info("Successfully connected to UPS server");
            connectionRequestedByAdmin = true;
        } else {
            log.error("Failed to connect to UPS server");
        }
        
        return connected;
    }
    
    /**
     * Connect to the UPS server with custom host and port
     */
    public boolean connectToUPS(String host, int port) {
        log.info("Connecting to UPS server at {}:{}", host, port);
        
        boolean connected = upsService.connect(host, port);
        
        if (connected) {
            log.info("Successfully connected to UPS server");
            connectionRequestedByAdmin = true;
        } else {
            log.error("Failed to connect to UPS server");
        }
        
        return connected;
    }
    
    /**
     * Disconnect from the UPS server
     */
    public void disconnectFromUPS() {
        upsService.disconnect();
        connectionRequestedByAdmin = false;
        log.info("Disconnected from UPS server");
    }

    /**
     * Periodically check and reconnect to UPS if a connection was previously 
     * established by an admin and the connection is now lost
     */
    @Scheduled(fixedDelay = 60000)  // Check every minute
    public void reconnectIfNeeded() {
        // Only try to reconnect if an admin previously requested a connection
        if (connectionRequestedByAdmin && !upsService.isConnected()) {
            log.info("UPS connection is down, attempting to reconnect");
            connectToUPS();
        }
    }
} 