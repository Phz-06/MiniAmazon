package edu.ece568.MiniAmazon.service;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.GenericMessage;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.ShipmentRequest;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.LoadConfirmation;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.TruckAssignment;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.DeliveryUpdate;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.DeliveryCompleted;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.Ack;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.Error;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.Coordinates;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class UPSService {
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    
    // Track the seqNum for messages sent to UPS
    private final AtomicLong seqNum = new AtomicLong(1);
    
    // Track pending messages waiting for Ack
    private final Map<Long, GenericMessage> pendingMessages = new ConcurrentHashMap<>();
    
    // Track retry counts for each message
    private final Map<Long, Integer> retryCount = new ConcurrentHashMap<>();
    
    // Queue for messages received from UPS
    private final BlockingQueue<GenericMessage> messageQueue = new LinkedBlockingQueue<>();
    
    // Thread for response handling
    private Thread responseListenerThread;
    private boolean running = false;
    
    @Getter
    private boolean connected = false;
    
    // Cache of processed seqnums to avoid duplicates
    private final List<Long> processedSeqnums = new ArrayList<>();
    private static final int MAX_PROCESSED_CACHE = 1000;
    
    // Maximum retries for messages
    private static final int MAX_RETRIES = 3;
    
    // Timeout for Ack (in milliseconds)
    private static final long ACK_TIMEOUT = 5000;
    
    /**
     * Connect to the UPS server
     *
     * @param host Hostname of the UPS server
     * @param port Port number
     * @return true if connection was successful
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            
            log.info("Connected to UPS server at {}:{}", host, port);
            connected = true;
            
            // Start the response listener thread
            startResponseListener();
            
            return true;
        } catch (Exception e) {
            log.error("Failed to connect to UPS server", e);
            return false;
        }
    }
    
    /**
     * Start a background thread to listen for messages from UPS
     */
    private void startResponseListener() {
        if (responseListenerThread != null && responseListenerThread.isAlive()) {
            return;
        }
        
        running = true;
        responseListenerThread = new Thread(() -> {
            while (running && !socket.isClosed()) {
                try {
                    GenericMessage message = receiveMessage();
                    
                    // Handle acknowledgments
                    if (message.hasAck()) {
                        handleAcknowledgment(message.getAck());
                        continue;
                    }
                    
                    // Send acknowledgment for received message
                    sendAcknowledgment(message.getSeqnum());
                    
                    // Check for duplicates
                    if (isDuplicate(message.getSeqnum())) {
                        log.info("Received duplicate message with seqnum: {}, ignoring", message.getSeqnum());
                        continue;
                    }
                    
                    // Add to processed seqnums
                    addToProcessedSeqnums(message.getSeqnum());
                    
                    // Add to queue for processing
                    messageQueue.put(message);
                } catch (Exception e) {
                    log.error("Error receiving messages from UPS", e);
                    if (!socket.isClosed()) {
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            log.error("Error closing socket", ex);
                        }
                    }
                    running = false;
                    connected = false;
                }
            }
        });
        responseListenerThread.setDaemon(true);
        responseListenerThread.start();
    }
    
    /**
     * Get the next message from the queue
     * @return the next message or null if none is available
     */
    public GenericMessage getNextMessage() {
        return messageQueue.poll();
    }
    
    /**
     * Send a shipment request to UPS
     *
     * @param packageId The unique package ID
     * @param warehouseId The warehouse ID
     * @param destX The destination X coordinate
     * @param destY The destination Y coordinate
     * @param upsAccount The UPS account (optional)
     * @return The sequence number of the sent message
     */
    public long sendShipmentRequest(String packageId, int warehouseId, int destX, int destY, String upsAccount) {
        long msgSeqNum = seqNum.getAndIncrement();
        
        ShipmentRequest.Builder builder = ShipmentRequest.newBuilder()
                .setPackageId(packageId)
                .setWarehouseId(warehouseId)
                .setDestination(Coordinates.newBuilder().setX(destX).setY(destY).build())
                .setSeqnum(msgSeqNum);
        
        if (upsAccount != null && !upsAccount.isEmpty()) {
            builder.setUpsAccount(upsAccount);
        }
        
        GenericMessage message = GenericMessage.newBuilder()
                .setType("shipment_request")
                .setSeqnum(msgSeqNum)
                .setShipmentRequest(builder.build())
                .build();
        
        try {
            // Store message for potential retry
            pendingMessages.put(msgSeqNum, message);
            retryCount.put(msgSeqNum, 0);
            
            // Send the message
            sendMessage(message);
            
            log.info("Sent shipment request for package: {}, seqnum: {}", packageId, msgSeqNum);
            return msgSeqNum;
        } catch (Exception e) {
            log.error("Failed to send shipment request for package: {}", packageId, e);
            pendingMessages.remove(msgSeqNum);
            retryCount.remove(msgSeqNum);
            return -1;
        }
    }
    
    /**
     * Send load confirmation to UPS
     *
     * @param packageId The package ID
     * @param truckId The truck ID
     * @return The sequence number of the sent message
     */
    public long sendLoadConfirmation(String packageId, int truckId) {
        long msgSeqNum = seqNum.getAndIncrement();
        
        LoadConfirmation loadConfirmation = LoadConfirmation.newBuilder()
                .setPackageId(packageId)
                .setTruckId(truckId)
                .setSeqnum(msgSeqNum)
                .build();
        
        GenericMessage message = GenericMessage.newBuilder()
                .setType("load_confirmation")
                .setSeqnum(msgSeqNum)
                .setLoadConfirmation(loadConfirmation)
                .build();
        
        try {
            // Store message for potential retry
            pendingMessages.put(msgSeqNum, message);
            retryCount.put(msgSeqNum, 0);
            
            // Send the message
            sendMessage(message);
            
            log.info("Sent load confirmation for package: {}, truck: {}, seqnum: {}", 
                    packageId, truckId, msgSeqNum);
            return msgSeqNum;
        } catch (Exception e) {
            log.error("Failed to send load confirmation for package: {}, truck: {}", 
                    packageId, truckId, e);
            pendingMessages.remove(msgSeqNum);
            retryCount.remove(msgSeqNum);
            return -1;
        }
    }
    
    /**
     * Handle acknowledgment received from UPS
     *
     * @param ack The acknowledgment message
     */
    public void handleAcknowledgment(Ack ack) {
        long ackSeqNum = ack.getSeqnum();
        log.debug("Received acknowledgment for seqnum: {}", ackSeqNum);
        
        // Remove from pending messages
        pendingMessages.remove(ackSeqNum);
        retryCount.remove(ackSeqNum);
    }
    
    /**
     * Send acknowledgment for a received message
     *
     * @param seqNumToAck The sequence number to acknowledge
     */
    private void sendAcknowledgment(long seqNumToAck) throws IOException {
        Ack ack = Ack.newBuilder().setSeqnum(seqNumToAck).build();
        
        GenericMessage message = GenericMessage.newBuilder()
                .setType("ack")
                .setAck(ack)
                .build();
        
        sendMessage(message);
        log.debug("Sent acknowledgment for seqnum: {}", seqNumToAck);
    }
    
    /**
     * Check if a sequence number has been processed already
     */
    private boolean isDuplicate(long seqNum) {
        return processedSeqnums.contains(seqNum);
    }
    
    /**
     * Add a sequence number to the list of processed numbers
     */
    private void addToProcessedSeqnums(long seqNum) {
        processedSeqnums.add(seqNum);
        
        // Keep the cache size limited
        if (processedSeqnums.size() > MAX_PROCESSED_CACHE) {
            processedSeqnums.remove(0);
        }
    }
    
    /**
     * Scheduled method to check for messages that need to be retried
     */
    @Scheduled(fixedDelay = 1000)
    public void retryPendingMessages() {
        if (!connected || pendingMessages.isEmpty()) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<Long, GenericMessage> entry : new HashMap<>(pendingMessages).entrySet()) {
            long msgSeqNum = entry.getKey();
            GenericMessage message = entry.getValue();
            
            // Skip if recently sent
            if (!retryCount.containsKey(msgSeqNum)) {
                continue;
            }
            
            int currentRetryCount = retryCount.get(msgSeqNum);
            
            // Check if we've exceeded max retries
            if (currentRetryCount >= MAX_RETRIES) {
                log.error("Max retries exceeded for seqnum: {}, giving up", msgSeqNum);
                pendingMessages.remove(msgSeqNum);
                retryCount.remove(msgSeqNum);
                continue;
            }
            
            // Retry sending the message
            try {
                sendMessage(message);
                retryCount.put(msgSeqNum, currentRetryCount + 1);
                log.info("Retrying message with seqnum: {}, retry count: {}", 
                        msgSeqNum, currentRetryCount + 1);
            } catch (Exception e) {
                log.error("Failed to retry message with seqnum: {}", msgSeqNum, e);
            }
        }
    }
    
    /**
     * Send a message to UPS
     */
    private void sendMessage(GenericMessage message) throws IOException {
        if (outputStream == null) {
            throw new IOException("Not connected to UPS server");
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
    }
    
    /**
     * Receive a message from UPS
     */
    private GenericMessage receiveMessage() throws IOException {
        if (inputStream == null) {
            throw new IOException("Not connected to UPS server");
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
     * Close the connection to UPS
     */
    @PreDestroy
    public void disconnect() {
        if (socket != null && !socket.isClosed()) {
            try {
                running = false;
                socket.close();
                log.info("Disconnected from UPS server");
            } catch (IOException e) {
                log.error("Error closing UPS connection", e);
            }
        }
    }
    
    /**
     * Process an incoming message from the UPS socket server
     * This is called by the UPSSocketServerService when it receives a message
     * 
     * @param message The message received from UPS
     */
    public void processIncomingMessage(GenericMessage message) {
        try {
            // Check for duplicates
            if (isDuplicate(message.getSeqnum())) {
                log.info("Received duplicate message with seqnum: {}, ignoring", message.getSeqnum());
                return;
            }
            
            // Add to processed seqnums
            addToProcessedSeqnums(message.getSeqnum());
            
            // Add to queue for processing by the response processor
            messageQueue.put(message);
            
            log.info("Added incoming message to processing queue: {}", message.getType());
        } catch (Exception e) {
            log.error("Error processing incoming message from UPS", e);
        }
    }
    
    /**
     * Handle an acknowledgment received from UPS through the socket server
     * 
     * @param ack The acknowledgment received
     */
    public void handleIncomingAck(Ack ack) {
        handleAcknowledgment(ack);
    }
    
    /**
     * Create an acknowledgment message for the given sequence number
     * This is used by the UPSSocketServerService to send acknowledgments
     * 
     * @param seqNumToAck The sequence number to acknowledge
     * @return The acknowledgment message
     */
    public GenericMessage createAcknowledgmentMessage(long seqNumToAck) {
        Ack ack = Ack.newBuilder().setSeqnum(seqNumToAck).build();
        
        return GenericMessage.newBuilder()
                .setType("ack")
                .setAck(ack)
                .build();
    }
} 