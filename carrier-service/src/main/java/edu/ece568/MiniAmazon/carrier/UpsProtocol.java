package edu.ece568.MiniAmazon.carrier;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import edu.ece568.MiniAmazon.proto.WorldUps.UCommands;
import edu.ece568.MiniAmazon.proto.WorldUps.UConnect;
import edu.ece568.MiniAmazon.proto.WorldUps.UConnected;
import edu.ece568.MiniAmazon.proto.WorldUps.UDeliveryLocation;
import edu.ece568.MiniAmazon.proto.WorldUps.UGoDeliver;
import edu.ece568.MiniAmazon.proto.WorldUps.UGoPickup;
import edu.ece568.MiniAmazon.proto.WorldUps.UInitTruck;
import edu.ece568.MiniAmazon.proto.WorldUps.UResponses;
import edu.ece568.MiniAmazon.shared.worldlink.StoredCommand;
import edu.ece568.MiniAmazon.shared.worldlink.WorldProtocol;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

/** The carrier dialect of the world protocol. */
public class UpsProtocol implements WorldProtocol<UCommands, UResponses> {
    public static final String PICKUP = "PICKUP";
    public static final String DELIVER = "DELIVER";

    /** What the world puts in UFinished.status. */
    public static final String ARRIVED_AT_WAREHOUSE = "ARRIVE WAREHOUSE";
    public static final String IDLE = "IDLE";

    private final int trucks;

    public UpsProtocol(int trucks) {
        this.trucks = trucks;
    }

    @Override
    public MessageLite hello(Long worldId, boolean initialise) {
        UConnect.Builder hello = UConnect.newBuilder().setIsAmazon(false);
        if (worldId != null) {
            hello.setWorldid(worldId);
        }
        if (initialise) {
            for (int id = 1; id <= trucks; id++) {
                hello.addTrucks(UInitTruck.newBuilder().setId(id).setX(0).setY(0));
            }
        }
        return hello.build();
    }

    @Override
    public Handshake readHandshake(InputStream in) throws IOException {
        UConnected reply = UConnected.parseDelimitedFrom(in);
        return reply == null ? null : new Handshake(reply.getWorldid(), reply.getResult());
    }

    @Override
    public UResponses readResponses(InputStream in) throws IOException {
        return UResponses.parseDelimitedFrom(in);
    }

    @Override
    public List<Long> acksIn(UResponses responses) {
        return responses.getAcksList();
    }

    @Override
    public UCommands batch(List<StoredCommand> commands, BiConsumer<StoredCommand, String> unreadable) {
        UCommands.Builder batch = UCommands.newBuilder();
        for (StoredCommand command : commands) {
            try {
                switch (command.type()) {
                    case PICKUP -> batch.addPickups(UGoPickup.parseFrom(command.payload()));
                    case DELIVER -> batch.addDeliveries(UGoDeliver.parseFrom(command.payload()));
                    default -> unreadable.accept(command, "unknown type " + command.type());
                }
            } catch (InvalidProtocolBufferException e) {
                unreadable.accept(command, e.getMessage());
            }
        }
        return batch.build();
    }

    @Override
    public UCommands acks(List<Long> seqnums) {
        return UCommands.newBuilder().addAllAcks(seqnums).build();
    }

    @Override
    public UCommands simSpeed(int speed) {
        return UCommands.newBuilder().setSimspeed(speed).build();
    }

    static byte[] pickup(long seq, int truckId, int warehouseId) {
        return UGoPickup.newBuilder().setSeqnum(seq).setTruckid(truckId).setWhid(warehouseId).build().toByteArray();
    }

    static byte[] deliver(long seq, int truckId, long packageId, int x, int y) {
        return UGoDeliver.newBuilder().setSeqnum(seq).setTruckid(truckId)
                .addPackages(UDeliveryLocation.newBuilder().setPackageid(packageId).setX(x).setY(y))
                .build().toByteArray();
    }
}
