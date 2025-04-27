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

    @Value("${amazon.server.port:8090}")
    private int serverPort;

    @Getter
    private int actualPort;

    private ServerSocket serverSocket;
    private Socket upsClientSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private CodedInputStream codedInputStream;

    private Thread serverThread;
    private Thread messageListenerThread;

    @Getter
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);

    @Getter
    private final AtomicBoolean clientConnected = new AtomicBoolean(false);

    private final BlockingQueue<GenericMessage> incomingMessageQueue = new LinkedBlockingQueue<>();

    private final UPSService upsService;

    public UPSSocketServerService(UPSService upsService) {
        this.upsService = upsService;
    }

    @Async
    public boolean startServer(Integer port) {
        if (serverRunning.get()) {
            log.info("UPS socket server is already running on port {}", actualPort);
            return true;
        }

        int listenPort = (port != null) ? port : serverPort;
        int maxRetries = 5;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                log.info("Attempting to start UPS socket server on port {}", listenPort);
                serverSocket = new ServerSocket(listenPort, 50, java.net.InetAddress.getByName("0.0.0.0"));
                serverRunning.set(true);
                actualPort = listenPort;

                log.info("UPS socket server started successfully on port {}", actualPort);

                serverThread = new Thread(this::acceptConnections);
                serverThread.setDaemon(true);
                serverThread.start();

                return true;
            } catch (java.net.BindException e) {
                log.warn("Port {} already in use, trying next port", listenPort);
                listenPort++;
            } catch (IOException e) {
                log.error("Failed to start UPS socket server", e);
                return false;
            }
        }

        log.error("Failed to start UPS socket server after {} attempts", maxRetries);
        return false;
    }

    public Integer getServerPort() {
        if (!serverRunning.get()) {
            return null;
        }
        return actualPort;
    }

    private void acceptConnections() {
        while (serverRunning.get() && !serverSocket.isClosed()) {
            try {
                log.info("Waiting for UPS to connect...");
                Socket clientSocket = serverSocket.accept();

                handleUpsConnection(clientSocket);
            } catch (IOException e) {
                if (serverRunning.get()) {
                    log.error("Error accepting UPS connection", e);
                }
            }
        }
    }

    private synchronized void handleUpsConnection(Socket clientSocket) {
        //closeClientConnection();

        try {
            this.upsClientSocket = clientSocket;
            this.inputStream = clientSocket.getInputStream();
            this.outputStream = clientSocket.getOutputStream();
            this.codedInputStream = CodedInputStream.newInstance(inputStream);
            log.info("UPS connected from {}", clientSocket.getInetAddress());
            clientConnected.set(true);

            messageListenerThread = new Thread(this::listenForMessages);
            messageListenerThread.setDaemon(true);
            messageListenerThread.start();
        } catch (IOException e) {
            log.error("Error setting up UPS client connection", e);
            closeClientConnection();
        }
    }

    private void listenForMessages() {
        while (clientConnected.get() && !upsClientSocket.isClosed()) {
            try {
                GenericMessage message = receiveMessage();

                log.info("Received message from UPS: {}", message.getType());

                if (message.hasAck()) {
                    upsService.handleIncomingAck(message.getAck());
                    continue;
                }

                sendAcknowledgment(message.getSeqnum());

                incomingMessageQueue.put(message);

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

    private void sendAcknowledgment(long seqNumToAck) throws IOException {
        GenericMessage ackMessage = upsService.createAcknowledgmentMessage(seqNumToAck);
        sendMessage(ackMessage);
        log.debug("Sent acknowledgment for seqnum: {} (for compatibility)", seqNumToAck);
    }

    public GenericMessage getNextIncomingMessage() {
        return incomingMessageQueue.poll();
    }

    public void sendMessage(GenericMessage message) throws IOException {
        if (outputStream == null || !clientConnected.get()) {
            throw new IOException("No UPS client connected");
        }

        int size = message.getSerializedSize();
        CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputStream);

        codedOutputStream.writeUInt32NoTag(size);
        message.writeTo(codedOutputStream);
        codedOutputStream.flush();

        log.debug("Sent message to UPS client: {}", message.getType());
    }

    private GenericMessage receiveMessage() throws IOException {
        if (codedInputStream == null || !clientConnected.get()) {
            throw new IOException("No UPS client connected");
        }

        int size = codedInputStream.readUInt32();
        byte[] messageBytes = codedInputStream.readRawBytes(size);
        return GenericMessage.parseFrom(messageBytes);
    }

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
                codedInputStream = null;
                clientConnected.set(false);
            }
        }
    }

    @PreDestroy
    public void stopServer() {
        serverRunning.set(false);
        closeClientConnection();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                log.info("UPS socket server stopped");
            } catch (IOException e) {
                log.error("Error stopping UPS socket server", e);
            }
        }
    }

    public boolean isUpsClientConnected() {
        return clientConnected.get();
    }
}