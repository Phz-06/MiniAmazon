package edu.ece568.MiniAmazon.service;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.GenericMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service that listens for incoming TCP connections from UPS
 */
@Slf4j
@Service
public class UPSSocketServerService {

    @Value("${amazon.server.port:8000}")
    private int serverPort;
    
    private ServerSocket serverSocket;
    private Socket upsClientSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    
    private Thread serverThread;
    private Thread messageListenerThread;
    
    @Getter
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);
    
    @Getter
    private final AtomicBoolean clientConnected = new AtomicBoolean(false);
    
    // Queue for messages received from UPS
    private final BlockingQueue<GenericMessage> incomingMessageQueue = new LinkedBlockingQueue<>();
    
    private final UPSService upsService;
    
    public UPSSocketServerService(UPSService upsService) {
        this.upsService = upsService;
    }
    
    /**
     * Start the TCP server to listen for UPS connections
     * 
     * @param port The port to listen on (optional, defaults to application property)
     * @return true if server started successfully
     */
    @Async
    public boolean startServer(Integer port) {
        if (serverRunning.get()) {
            log.info("UPS socket server is already running");
            return true;
        }
        
        try {
            int listenPort = (port != null) ? port : serverPort;
            serverSocket = new ServerSocket(listenPort);
            serverRunning.set(true);
            
            log.info("UPS socket server started on port {}", listenPort);
            
            // Start server thread to accept connections
            serverThread = new Thread(this::acceptConnections);
            serverThread.setDaemon(true);
            serverThread.start();
            
            return true;
        } catch (IOException e) {
            log.error("Failed to start UPS socket server", e);
            return false;
        }
    }
    
    /**
     * Accept connections from UPS
     */
    private void acceptConnections() {
        while (serverRunning.get() && !serverSocket.isClosed()) {
            try {
                log.info("Waiting for UPS to connect...");
                Socket clientSocket = serverSocket.accept();
                
                // Handle only one connection at a time
                handleUpsConnection(clientSocket);
            } catch (IOException e) {
                if (serverRunning.get()) {
                    log.error("Error accepting UPS connection", e);
                }
            }
        }
    }
    
    /**
     * Handle a connection from UPS
     */
    private synchronized void handleUpsConnection(Socket clientSocket) {
        // Close existing connection if any
        closeClientConnection();
        
        try {
            this.upsClientSocket = clientSocket;
            this.inputStream = clientSocket.getInputStream();
            this.outputStream = clientSocket.getOutputStream();
            
            log.info("UPS connected from {}", clientSocket.getInetAddress());
            clientConnected.set(true);
            
            // Start message listener thread
            messageListenerThread = new Thread(this::listenForMessages);
            messageListenerThread.setDaemon(true);
            messageListenerThread.start();
        } catch (IOException e) {
            log.error("Error setting up UPS client connection", e);
            closeClientConnection();
        }
    }
    
    /**
     * Listen for messages from the connected UPS client
     */
    private void listenForMessages() {
        while (clientConnected.get() && !upsClientSocket.isClosed()) {
            try {
                GenericMessage message = receiveMessage();
                
                // Process the received message
                log.info("Received message from UPS: {}", message.getType());
                

                if (message.hasAck()) {
                    upsService.handleIncomingAck(message.getAck());
                    continue;
                }

                sendAcknowledgment(message.getSeqnum());
                
                // Add to queue for processing
                incomingMessageQueue.put(message);
                
                // Forward to UPS service for processing
                upsService.processIncomingMessage(message);
                
            } catch (Exception e) {
                if (clientConnected.get()) {
                    log.error("Error receiving messages from UPS", e);
                    closeClientConnection();
                }
                break;
            }
        }
    }
    
    /**
     * Send an acknowledgment for a received message
     * This is maintained for protocol compatibility
     */
    private void sendAcknowledgment(long seqNumToAck) throws IOException {
        GenericMessage ackMessage = upsService.createAcknowledgmentMessage(seqNumToAck);
        sendMessage(ackMessage);
        log.debug("Sent acknowledgment for seqnum: {} (for compatibility)", seqNumToAck);
    }
    
    /**
     * Get the next message from the queue
     */
    public GenericMessage getNextIncomingMessage() {
        return incomingMessageQueue.poll();
    }
    
    /**
     * Send a message to the connected UPS client
     */
    public void sendMessage(GenericMessage message) throws IOException {
        if (outputStream == null || !clientConnected.get()) {
            throw new IOException("No UPS client connected");
        }
        
        // Get size of message
        int size = message.getSerializedSize();
        
        // Create coded output stream
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
        
        // Write size as varint32
        codedOutputStream.writeUInt32NoTag(size);
        
        // Write message
        message.writeTo(codedOutputStream);
        
        // Flush data
        codedOutputStream.flush();
        
        log.debug("Sent message to UPS client: {}", message.getType());
    }
    
    /**
     * Receive a message from the connected UPS client
     */
    private GenericMessage receiveMessage() throws IOException {
        if (inputStream == null || !clientConnected.get()) {
            throw new IOException("No UPS client connected");
        }
        
        // Create coded input stream
        CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
        
        // Read size
        int size = codedInputStream.readUInt32();
        
        // Read message
        byte[] messageBytes = codedInputStream.readRawBytes(size);
        
        // Parse message
        return GenericMessage.parseFrom(messageBytes);
    }
    
    /**
     * Close the connection to the UPS client
     */
    private void closeClientConnection() {
        if (upsClientSocket != null && !upsClientSocket.isClosed()) {
            try {
                upsClientSocket.close();
                log.info("Closed connection to UPS client");
            } catch (IOException e) {
                log.error("Error closing UPS client connection", e);
            } finally {
                upsClientSocket = null;
                inputStream = null;
                outputStream = null;
                clientConnected.set(false);
            }
        }
    }
    
    /**
     * Stop the server
     */
    @PreDestroy
    public void stopServer() {
        serverRunning.set(false);
        
        // Close client connection
        closeClientConnection();
        
        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log.info("UPS socket server stopped");
            } catch (IOException e) {
                log.error("Error stopping UPS socket server", e);
            }
        }
    }
    
    /**
     * Check if a UPS client is connected
     */
    public boolean isUpsClientConnected() {
        return clientConnected.get();
    }
} 