package edu.ece568.MiniAmazon.service;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Message;
import edu.ece568.MiniAmazon.CatalogInitializer;
import edu.ece568.MiniAmazon.model.WareHouse;
import edu.ece568.MiniAmazon.repository.WareHouseRepository;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnect;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnected;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AInitWarehouse;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APack;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APutOnTruck;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AQuery;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class WorldService {
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    
    private final WareHouseRepository wareHouseRepository;

    private final CatalogInitializer catalogInitializer;

    @Value("${world.host:localhost}")
    private String worldHost;

    @Value("${world.port:23456}")
    private int worldPort;

    /**
     * -- GETTER --
     *  Get the world ID
     *
     */
    @Getter
    private Long worldId;
    private AtomicInteger seqNum = new AtomicInteger(1);
    /**
     * -- GETTER --
     *  Check if connected to world
     *
     */
    @Getter
    private boolean isConnected = false;

    // Thread for response handling
    private Thread responseListenerThread;
    private boolean running = false;

    // Queue for responses
    private BlockingQueue<AResponses> responseQueue = new LinkedBlockingQueue<>();
    
    public WorldService(WareHouseRepository wareHouseRepository, CatalogInitializer catalogInitializer) {
        this.wareHouseRepository = wareHouseRepository;
        this.catalogInitializer = catalogInitializer;
    }

    /**
     * Connect to the World Simulator
     *
     * @param host Hostname of the simulator
     * @param port Port number (23456 for Amazon)
     * @param worldId Optional worldId. If null, a new world will be created
     * @return true if connection was successful
     */
    public boolean connect(String host, int port, Long worldId) {
        try {
            this.worldId = worldId;

            log.info("Connecting to world simulator at {}:{}", worldHost, worldPort);
            socket = new Socket(host, port);
            //socket = new Socket(worldHost, worldPort);
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

            // Create AConnect message
            AConnect.Builder connectBuilder = AConnect.newBuilder();
            connectBuilder.setIsAmazon(true);

            if (worldId != null) {
                connectBuilder.setWorldid(worldId);
            }

            // Initialize the warehouse
            AInitWarehouse.Builder warehouseBuilder = AInitWarehouse.newBuilder();
            warehouseBuilder.setId(1);
            warehouseBuilder.setX(10);
            warehouseBuilder.setY(20);
            connectBuilder.addInitwh(warehouseBuilder.build());

            // Create the warehouse in our database as well
            WareHouse warehouse = wareHouseRepository.findById(1L).orElseGet(() -> {
                WareHouse newWarehouse = new WareHouse();
                newWarehouse.setName("Warehouse 1");
                newWarehouse.setXCoordinate(10);
                newWarehouse.setYCoordinate(20);
                log.info("Creating warehouse in database with coordinates: (10, 20)");
                return wareHouseRepository.save(newWarehouse);
            });
            
            log.info("Warehouse in database has ID: {}", warehouse.getId());

            AConnect connectMessage = connectBuilder.build();

            // Send the connect message
            sendMessage(connectMessage);

            // Receive the connected response
            AConnected connectedResponse = receiveMessage(AConnected.parser());

            // Check if connection was successful
            if (connectedResponse.getResult().equals("connected!")) {
                log.info("Successfully connected to world simulator. World ID: {}", connectedResponse.getWorldid());

                isConnected = true;

                // Start the response listener thread
                startResponseListener();

                // Initialize the initial catalog
                catalogInitializer.run();
                this.worldId = connectedResponse.getWorldid();
                return true;
            } else {
                log.error("Failed to connect to world simulator: {}", connectedResponse.getResult());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to connect to world service", e);
            return false;
        }
    }

    /**
     * Start a background thread to listen for responses from the world
     */
    private void startResponseListener() {
        if (responseListenerThread != null && responseListenerThread.isAlive()) {
            return;
        }

        running = true;
        responseListenerThread = new Thread(() -> {
            while (running && !socket.isClosed()) {
                try {
                    AResponses responses = receiveMessage(AResponses.parser());

                    // Handle acknowledgments by sending them back
                    if (responses.getAcksCount() > 0) {
                        List<Long> acks = new ArrayList<>(responses.getAcksList());
                        sendAcks(acks);
                    }

                    // Add to queue for processing
                    responseQueue.put(responses);
                } catch (Exception e) {
                    log.error("Error receiving responses from world", e);
                    if (!socket.isClosed()) {
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            log.error("Error closing socket", ex);
                        }
                    }
                    running = false;
                }
            }
        });
        responseListenerThread.setDaemon(true);
        responseListenerThread.start();
    }

    /**
     * Get the next response from the queue
     * @return the next response or null if none is available
     */
    public AResponses getNextResponse() {
        return responseQueue.poll();
    }

    /**
     * Send acknowledgments for received messages
     * @param acks list of sequence numbers to acknowledge
     */
    private void sendAcks(List<Long> acks) {
        try {
            ACommands.Builder builder = ACommands.newBuilder();
            builder.addAllAcks(acks);
            sendMessage(builder.build());
        } catch (Exception e) {
            log.error("Error sending acknowledgments", e);
        }
    }

    /**
     * Send a Protocol Buffer message to the world
     * @param message the message to send
     */
    private <T extends Message> void sendMessage(T message) throws IOException {
        synchronized (outputStream) {
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);
            codedOutputStream.writeUInt32NoTag(message.getSerializedSize());
            message.writeTo(codedOutputStream);
            codedOutputStream.flush();
            outputStream.flush();
        }
    }

    /**
     * Receive a Protocol Buffer message from the world
     * @param parser the parser for the expected message type
     * @return the received message
     */
    private <T extends Message> T receiveMessage(com.google.protobuf.Parser<T> parser) throws IOException {
        synchronized (inputStream) {
            CodedInputStream codedInputStream = CodedInputStream.newInstance(inputStream);
            int size = codedInputStream.readRawVarint32();
            int limit = codedInputStream.pushLimit(size);
            T message = parser.parseFrom(codedInputStream);
            codedInputStream.popLimit(limit);
            return message;
        }
    }

    /**
     * Buy products for a warehouse
     * @param warehouseId the ID of the warehouse
     * @param productId the ID of the product
     * @param description the description of the product
     * @param count the number of products to buy
     * @return the sequence number of the request
     */
    public long buyProduct(int warehouseId, long productId, String description, int count) {
        try {
            // Create purchase message
            APurchaseMore.Builder purchaseBuilder = APurchaseMore.newBuilder();
            purchaseBuilder.setWhnum(warehouseId);

            // Get a new sequence number
            long seq = seqNum.getAndIncrement();
            purchaseBuilder.setSeqnum(seq);

            // Add the product
            AProduct.Builder productBuilder = AProduct.newBuilder();
            productBuilder.setId(productId);
            productBuilder.setDescription(description);
            productBuilder.setCount(count);
            purchaseBuilder.addThings(productBuilder.build());

            // Create command message
            ACommands.Builder commandBuilder = ACommands.newBuilder();
            commandBuilder.addBuy(purchaseBuilder.build());

            // Send the command
            sendMessage(commandBuilder.build());

            log.info("Sent buy command for product {} ({}), quantity {}, to warehouse {}",
                    productId, description, count, warehouseId);

            return seq;
        } catch (Exception e) {
            log.error("Error sending buy command", e);
            return -1;
        }
    }

    /**
     * Request to pack an order
     * @param warehouseId the ID of the warehouse
     * @param shipmentId the ID of the shipment
     * @param products list of products in the format [[id, count], [id, count], ...]
     * @return the sequence number of the request
     */
    public long packOrder(int warehouseId, long shipmentId, List<Object[]> products) {
        try {
            // Create pack message
            APack.Builder packBuilder = APack.newBuilder();
            packBuilder.setWhnum(warehouseId);
            packBuilder.setShipid(shipmentId);

            // Get a new sequence number
            long seq = seqNum.getAndIncrement();
            packBuilder.setSeqnum(seq);

            // Add the products
            for (Object[] product : products) {
                long productId = Long.parseLong((String) product[0]);
                int count = (Integer) product[2];
                String description = (String) product[1];

                AProduct.Builder productBuilder = AProduct.newBuilder();
                productBuilder.setId(productId);
                productBuilder.setDescription(description);
                productBuilder.setCount(count);
                packBuilder.addThings(productBuilder.build());
            }

            // Create command message
            ACommands.Builder commandBuilder = ACommands.newBuilder();
            commandBuilder.addTopack(packBuilder.build());

            // Send the command
            sendMessage(commandBuilder.build());

            log.info("Sent pack command for shipment {} with {} products from warehouse {}",
                    shipmentId, products.size(), warehouseId);

            return seq;
        } catch (Exception e) {
            log.error("Error sending pack command", e);
            return -1;
        }
    }

    /**
     * Request to load a package onto a truck
     * @param warehouseId the ID of the warehouse
     * @param truckId the ID of the truck
     * @param shipmentId the ID of the shipment
     * @return the sequence number of the request
     */
    public long loadPackage(int warehouseId, int truckId, long shipmentId) {
        try {
            // Create load message
            APutOnTruck.Builder loadBuilder = APutOnTruck.newBuilder();
            loadBuilder.setWhnum(warehouseId);
            loadBuilder.setTruckid(truckId);
            loadBuilder.setShipid(shipmentId);

            // Get a new sequence number
            long seq = seqNum.getAndIncrement();
            loadBuilder.setSeqnum(seq);

            // Create command message
            ACommands.Builder commandBuilder = ACommands.newBuilder();
            commandBuilder.addLoad(loadBuilder.build());

            // Send the command
            sendMessage(commandBuilder.build());

            log.info("Sent load command for shipment {} onto truck {} at warehouse {}",
                    shipmentId, truckId, warehouseId);

            return seq;
        } catch (Exception e) {
            log.error("Error sending load command", e);
            return -1;
        }
    }

    /**
     * Query the status of a package
     * @param packageId the ID of the package
     * @return the sequence number of the request
     */
    public long queryPackage(long packageId) {
        try {
            // Create query message
            AQuery.Builder queryBuilder = AQuery.newBuilder();
            queryBuilder.setPackageid(packageId);

            // Get a new sequence number
            long seq = seqNum.getAndIncrement();
            queryBuilder.setSeqnum(seq);

            // Create command message
            ACommands.Builder commandBuilder = ACommands.newBuilder();
            commandBuilder.addQueries(queryBuilder.build());

            // Send the command
            sendMessage(commandBuilder.build());

            log.info("Sent query command for package {}", packageId);

            return seq;
        } catch (Exception e) {
            log.error("Error sending query command", e);
            return -1;
        }
    }

    /**
     * Set the simulation speed
     * @param speed the simulation speed (higher is faster)
     */
    public void setSimulationSpeed(int speed) {
        try {
            // Create command message
            ACommands.Builder commandBuilder = ACommands.newBuilder();
            commandBuilder.setSimspeed(speed);

            // Send the command
            sendMessage(commandBuilder.build());

            log.info("Set simulation speed to {}", speed);
        } catch (Exception e) {
            log.error("Error setting simulation speed", e);
        }
    }

    /**
     * Disconnect from the world
     */
    @PreDestroy
    public void disconnect() {
        if (isConnected) {
            try {
                // Send disconnect command
                ACommands.Builder builder = ACommands.newBuilder();
                builder.setDisconnect(true);
                sendMessage(builder.build());

                // Wait for response handler to finish
                running = false;
                if (responseListenerThread != null) {
                    responseListenerThread.join(5000);
                }

                // Close socket
                if (!socket.isClosed()) {
                    socket.close();
                }

                isConnected = false;
                log.info("Disconnected from world simulator");
            } catch (Exception e) {
                log.error("Error during disconnect", e);
            }
        }
    }

}
