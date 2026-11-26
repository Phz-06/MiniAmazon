package edu.ece568.MiniAmazon.shared.testing;

import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnect;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnected;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AErr;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ALoaded;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APack;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APacked;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APutOnTruck;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;

import edu.ece568.MiniAmazon.proto.WorldUps.UCommands;
import edu.ece568.MiniAmazon.proto.WorldUps.UConnect;
import edu.ece568.MiniAmazon.proto.WorldUps.UConnected;
import edu.ece568.MiniAmazon.proto.WorldUps.UDeliveryLocation;
import edu.ece568.MiniAmazon.proto.WorldUps.UDeliveryMade;
import edu.ece568.MiniAmazon.proto.WorldUps.UFinished;
import edu.ece568.MiniAmazon.proto.WorldUps.UGoDeliver;
import edu.ece568.MiniAmazon.proto.WorldUps.UGoPickup;
import edu.ece568.MiniAmazon.proto.WorldUps.UInitTruck;
import edu.ece568.MiniAmazon.proto.WorldUps.UResponses;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A stand-in for the course's world simulator that speaks the same protocol
 * and is deliberately hostile:
 *
 *   - it ignores a configurable share of the messages it receives,
 *   - it loses a share of the messages it sends,
 *   - it resends everything unacknowledged every few milliseconds, so
 *     duplicates are the normal case, not the exception,
 *   - its connection can be cut at any moment.
 *
 * It serves both dialects: the warehouse side on port() and the carrier side
 * on upsPort(). It counts how many times each pack, load, pickup and delivery
 * actually took effect, which is what the tests assert on. Like the real simulator it executes a command
 * sequence number once and only acknowledges it on later deliveries, and its
 * state survives a reconnect to the same world id.
 */
public class FakeWorld implements Closeable {

    private final ServerSocket server;
    private final ServerSocket upsServer;
    private final Random random = new Random(7);
    private volatile double dropRate;
    private volatile boolean closed;

    private final Object state = new Object();
    private long worldId;
    private long nextSeq = 1;
    private final Map<Long, Integer> stock = new HashMap<>();
    private final Set<Long> seenCommands = new HashSet<>();
    private final Set<Long> packed = new HashSet<>();
    private final Set<Long> loaded = new HashSet<>();
    private final Map<Long, Integer> packExecutions = new HashMap<>();
    private final Map<Long, Integer> loadExecutions = new HashMap<>();
    private final Map<Long, Consumer<AResponses.Builder>> unacked = new TreeMap<>();

    private final Set<Integer> trucks = new HashSet<>();
    private final Map<Integer, Integer> pickupExecutions = new HashMap<>();
    private final Map<Long, Integer> deliveryExecutions = new HashMap<>();
    private final Map<Long, Consumer<UResponses.Builder>> upsUnacked = new TreeMap<>();
    private volatile Socket upsCurrent;

    private volatile Socket current;
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicInteger droppedIncoming = new AtomicInteger();
    private final AtomicInteger redeliveries = new AtomicInteger();

    public FakeWorld() {
        try {
            server = new ServerSocket(0);
            upsServer = new ServerSocket(0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        daemon("fake-world-accept", this::acceptLoop);
        daemon("fake-world-resend", this::resendLoop);
        daemon("fake-world-ups-accept", this::upsAcceptLoop);
        daemon("fake-world-ups-resend", this::upsResendLoop);
    }

    public int port() {
        return server.getLocalPort();
    }

    public int upsPort() {
        return upsServer.getLocalPort();
    }

    /** For carrier tests, where no warehouse side connects to create the world. */
    public long createWorld() {
        synchronized (state) {
            return ++worldId;
        }
    }

    /**
     * What the real simulator does when it restarts: every world is gone, and
     * both world ids and its message sequence numbers start from 1 again. The
     * next world created will carry the same id as the one just lost.
     */
    public void loseAllWorlds() {
        synchronized (state) {
            worldId = 0;
            nextSeq = 1;
            seenCommands.clear();
            stock.clear();
            packed.clear();
            loaded.clear();
            trucks.clear();
            unacked.clear();
            upsUnacked.clear();
        }
        killConnection();
    }

    /** Share of messages lost in each direction, 0.0 to 1.0. */
    public void dropRate(double rate) {
        this.dropRate = rate;
    }

    /** Cuts the current connection, as a network failure would. */
    public void killConnection() {
        for (Socket socket : new Socket[]{current, upsCurrent}) {
            if (socket != null) {
                closeQuietly(socket);
            }
        }
    }

    public int trucksRegistered() {
        synchronized (state) {
            return trucks.size();
        }
    }

    /** Pickup trips actually started, over all trucks. */
    public int pickups() {
        synchronized (state) {
            return pickupExecutions.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public int timesDelivered(long packageId) {
        synchronized (state) {
            return deliveryExecutions.getOrDefault(packageId, 0);
        }
    }

    public int stockOf(long productId) {
        synchronized (state) {
            return stock.getOrDefault(productId, 0);
        }
    }

    public int timesPacked(long shipId) {
        synchronized (state) {
            return packExecutions.getOrDefault(shipId, 0);
        }
    }

    public int timesLoaded(long shipId) {
        synchronized (state) {
            return loadExecutions.getOrDefault(shipId, 0);
        }
    }

    public int connections() {
        return connections.get();
    }

    public int droppedIncoming() {
        return droppedIncoming.get();
    }

    public int redeliveries() {
        return redeliveries.get();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = server.accept();
                daemon("fake-world-conn", () -> serve(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            AConnect hello = AConnect.parseDelimitedFrom(in);
            if (hello == null) {
                return;
            }
            long id;
            synchronized (state) {
                if (!hello.hasWorldid()) {
                    worldId++;
                    stock.clear();
                    seenCommands.clear();
                    packed.clear();
                    loaded.clear();
                    unacked.clear();
                } else if (hello.getWorldid() != worldId) {
                    write(out, AConnected.newBuilder().setWorldid(hello.getWorldid())
                            .setResult("error: world does not exist").build()::writeDelimitedTo);
                    return;
                }
                id = worldId;
            }
            write(out, AConnected.newBuilder().setWorldid(id).setResult("connected!").build()::writeDelimitedTo);
            connections.incrementAndGet();
            current = socket;

            ACommands commands;
            while ((commands = ACommands.parseDelimitedFrom(in)) != null) {
                if (random.nextDouble() < dropRate) {
                    droppedIncoming.incrementAndGet();
                    continue; // as if it never arrived: no ack, no effect
                }
                AResponses.Builder reply = AResponses.newBuilder();
                synchronized (state) {
                    commands.getAcksList().forEach(unacked::remove);
                    for (APurchaseMore buy : commands.getBuyList()) {
                        reply.addAcks(buy.getSeqnum());
                        if (seenCommands.add(buy.getSeqnum())) {
                            purchase(buy);
                        }
                    }
                    for (APack pack : commands.getTopackList()) {
                        reply.addAcks(pack.getSeqnum());
                        if (seenCommands.add(pack.getSeqnum())) {
                            pack(pack);
                        }
                    }
                    for (APutOnTruck load : commands.getLoadList()) {
                        reply.addAcks(load.getSeqnum());
                        if (seenCommands.add(load.getSeqnum())) {
                            load(load);
                        }
                    }
                }
                if (reply.getAcksCount() > 0 && random.nextDouble() >= dropRate) {
                    AResponses message = reply.build();
                    write(out, message::writeDelimitedTo);
                }
                if (commands.getDisconnect()) {
                    return;
                }
            }
        } catch (IOException ignored) {
            // the client went away or the test cut the connection
        } finally {
            if (current == socket) {
                current = null;
            }
        }
    }

    private void purchase(APurchaseMore buy) {
        for (AProduct thing : buy.getThingsList()) {
            stock.merge(thing.getId(), thing.getCount(), Integer::sum);
        }
        long seq = nextSeq++;
        unacked.put(seq, b -> b.addArrived(buy.toBuilder().setSeqnum(seq)));
    }

    private void pack(APack pack) {
        long seq = nextSeq++;
        boolean enough = pack.getThingsList().stream()
                .allMatch(thing -> stock.getOrDefault(thing.getId(), 0) >= thing.getCount());
        if (!enough) {
            unacked.put(seq, b -> b.addError(AErr.newBuilder().setErr("error: not enough stock to pack")
                    .setOriginseqnum(pack.getSeqnum()).setSeqnum(seq)));
            return;
        }
        for (AProduct thing : pack.getThingsList()) {
            stock.merge(thing.getId(), -thing.getCount(), Integer::sum);
        }
        packed.add(pack.getShipid());
        packExecutions.merge(pack.getShipid(), 1, Integer::sum);
        unacked.put(seq, b -> b.addReady(APacked.newBuilder().setShipid(pack.getShipid()).setSeqnum(seq)));
    }

    private void load(APutOnTruck load) {
        long seq = nextSeq++;
        if (!packed.contains(load.getShipid())) {
            unacked.put(seq, b -> b.addError(AErr.newBuilder().setErr("error: package is not packed")
                    .setOriginseqnum(load.getSeqnum()).setSeqnum(seq)));
            return;
        }
        loaded.add(load.getShipid());
        loadExecutions.merge(load.getShipid(), 1, Integer::sum);
        unacked.put(seq, b -> b.addLoaded(ALoaded.newBuilder().setShipid(load.getShipid()).setSeqnum(seq)));
    }

    /** Everything not yet acknowledged goes out again, and again, until it is. */
    private void resendLoop() {
        Set<Long> sentBefore = new HashSet<>();
        while (!closed) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                return;
            }
            Socket socket = current;
            if (socket == null) {
                continue;
            }
            AResponses.Builder batch = AResponses.newBuilder();
            synchronized (state) {
                if (unacked.isEmpty()) {
                    continue;
                }
                unacked.forEach((seq, add) -> {
                    add.accept(batch);
                    if (!sentBefore.add(seq)) {
                        redeliveries.incrementAndGet();
                    }
                });
            }
            if (random.nextDouble() < dropRate) {
                continue; // lost on the way
            }
            try {
                AResponses message = batch.build();
                write(socket.getOutputStream(), message::writeDelimitedTo);
            } catch (IOException ignored) {
                // connection is gone; the next one gets the resend
            }
        }
    }

    // ---- carrier side ----------------------------------------------------

    private void upsAcceptLoop() {
        while (!closed) {
            try {
                Socket socket = upsServer.accept();
                daemon("fake-world-ups-conn", () -> serveUps(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serveUps(Socket socket) {
        try (socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            UConnect hello = UConnect.parseDelimitedFrom(in);
            if (hello == null) {
                return;
            }
            String refusal = null;
            synchronized (state) {
                if (!hello.hasWorldid() || hello.getWorldid() != worldId) {
                    refusal = "error: world is not available for ups to connect.";
                } else if (hello.getTrucksList().stream().anyMatch(t -> trucks.contains(t.getId()))) {
                    refusal = "error: warehouse_id/truck_id already exists.";
                } else {
                    for (UInitTruck truck : hello.getTrucksList()) {
                        trucks.add(truck.getId());
                    }
                }
            }
            if (refusal != null) {
                write(out, UConnected.newBuilder().setWorldid(hello.getWorldid()).setResult(refusal).build()::writeDelimitedTo);
                return;
            }
            write(out, UConnected.newBuilder().setWorldid(hello.getWorldid()).setResult("connected!").build()::writeDelimitedTo);
            connections.incrementAndGet();
            upsCurrent = socket;

            UCommands commands;
            while ((commands = UCommands.parseDelimitedFrom(in)) != null) {
                if (random.nextDouble() < dropRate) {
                    droppedIncoming.incrementAndGet();
                    continue;
                }
                UResponses.Builder reply = UResponses.newBuilder();
                synchronized (state) {
                    commands.getAcksList().forEach(upsUnacked::remove);
                    for (UGoPickup pickup : commands.getPickupsList()) {
                        reply.addAcks(pickup.getSeqnum());
                        if (seenCommands.add(-pickup.getSeqnum())) {
                            pickupExecutions.merge(pickup.getTruckid(), 1, Integer::sum);
                            long seq = nextSeq++;
                            upsUnacked.put(seq, b -> b.addCompletions(UFinished.newBuilder().setTruckid(pickup.getTruckid())
                                    .setX(10).setY(20).setStatus("ARRIVE WAREHOUSE").setSeqnum(seq)));
                        }
                    }
                    for (UGoDeliver deliver : commands.getDeliveriesList()) {
                        reply.addAcks(deliver.getSeqnum());
                        if (seenCommands.add(-deliver.getSeqnum())) {
                            for (UDeliveryLocation location : deliver.getPackagesList()) {
                                deliveryExecutions.merge(location.getPackageid(), 1, Integer::sum);
                                long seq = nextSeq++;
                                upsUnacked.put(seq, b -> b.addDelivered(UDeliveryMade.newBuilder()
                                        .setTruckid(deliver.getTruckid()).setPackageid(location.getPackageid()).setSeqnum(seq)));
                            }
                            long seq = nextSeq++;
                            upsUnacked.put(seq, b -> b.addCompletions(UFinished.newBuilder().setTruckid(deliver.getTruckid())
                                    .setX(0).setY(0).setStatus("IDLE").setSeqnum(seq)));
                        }
                    }
                }
                if (reply.getAcksCount() > 0 && random.nextDouble() >= dropRate) {
                    UResponses message = reply.build();
                    write(out, message::writeDelimitedTo);
                }
            }
        } catch (IOException ignored) {
            // the client went away or the test cut the connection
        } finally {
            if (upsCurrent == socket) {
                upsCurrent = null;
            }
        }
    }

    private void upsResendLoop() {
        Set<Long> sentBefore = new HashSet<>();
        while (!closed) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                return;
            }
            Socket socket = upsCurrent;
            if (socket == null) {
                continue;
            }
            UResponses.Builder batch = UResponses.newBuilder();
            synchronized (state) {
                if (upsUnacked.isEmpty()) {
                    continue;
                }
                upsUnacked.forEach((seq, add) -> {
                    add.accept(batch);
                    if (!sentBefore.add(seq)) {
                        redeliveries.incrementAndGet();
                    }
                });
            }
            if (random.nextDouble() < dropRate) {
                continue;
            }
            try {
                UResponses message = batch.build();
                write(socket.getOutputStream(), message::writeDelimitedTo);
            } catch (IOException ignored) {
                // connection is gone; the next one gets the resend
            }
        }
    }

    private interface Writer {
        void to(OutputStream out) throws IOException;
    }

    private static void write(OutputStream out, Writer writer) throws IOException {
        synchronized (out) {
            writer.to(out);
            out.flush();
        }
    }

    private static void daemon(String name, Runnable body) {
        Thread thread = new Thread(body, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    @Override
    public void close() {
        closed = true;
        killConnection();
        try {
            server.close();
            upsServer.close();
        } catch (IOException ignored) {
            // shutting down
        }
    }
}
