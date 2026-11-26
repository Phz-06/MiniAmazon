package edu.ece568.MiniAmazon.gateway;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import edu.ece568.MiniAmazon.proto.Events.Item;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnect;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AConnected;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AInitWarehouse;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APack;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APutOnTruck;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import edu.ece568.MiniAmazon.shared.worldlink.StoredCommand;
import edu.ece568.MiniAmazon.shared.worldlink.WorldProtocol;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;

/** The warehouse dialect of the world protocol. */
public class AmazonProtocol implements WorldProtocol<ACommands, AResponses> {
    public static final String PURCHASE = "PURCHASE";
    public static final String PACK = "PACK";
    public static final String LOAD = "LOAD";

    /** The one warehouse this application runs, and where it stands. */
    private static final AInitWarehouse WAREHOUSE = AInitWarehouse.newBuilder().setId(1).setX(10).setY(20).build();

    @Override
    public MessageLite hello(Long worldId, boolean initialise) {
        AConnect.Builder hello = AConnect.newBuilder().setIsAmazon(true);
        if (worldId != null) {
            hello.setWorldid(worldId);
        }
        if (initialise) {
            hello.addInitwh(WAREHOUSE);
        }
        return hello.build();
    }

    @Override
    public Handshake readHandshake(InputStream in) throws IOException {
        AConnected reply = AConnected.parseDelimitedFrom(in);
        return reply == null ? null : new Handshake(reply.getWorldid(), reply.getResult());
    }

    @Override
    public AResponses readResponses(InputStream in) throws IOException {
        return AResponses.parseDelimitedFrom(in);
    }

    @Override
    public List<Long> acksIn(AResponses responses) {
        return responses.getAcksList();
    }

    @Override
    public ACommands batch(List<StoredCommand> commands, BiConsumer<StoredCommand, String> unreadable) {
        ACommands.Builder batch = ACommands.newBuilder();
        for (StoredCommand command : commands) {
            try {
                switch (command.type()) {
                    case PURCHASE -> batch.addBuy(APurchaseMore.parseFrom(command.payload()));
                    case PACK -> batch.addTopack(APack.parseFrom(command.payload()));
                    case LOAD -> batch.addLoad(APutOnTruck.parseFrom(command.payload()));
                    default -> unreadable.accept(command, "unknown type " + command.type());
                }
            } catch (InvalidProtocolBufferException e) {
                unreadable.accept(command, e.getMessage());
            }
        }
        return batch.build();
    }

    @Override
    public ACommands acks(List<Long> seqnums) {
        return ACommands.newBuilder().addAllAcks(seqnums).build();
    }

    @Override
    public ACommands simSpeed(int speed) {
        return ACommands.newBuilder().setSimspeed(speed).build();
    }

    static byte[] purchase(long seq, int warehouse, List<Item> items) {
        return APurchaseMore.newBuilder().setSeqnum(seq).setWhnum(warehouse).addAllThings(things(items)).build().toByteArray();
    }

    static byte[] pack(long seq, int warehouse, long orderId, List<Item> items) {
        return APack.newBuilder().setSeqnum(seq).setWhnum(warehouse).setShipid(orderId).addAllThings(things(items)).build().toByteArray();
    }

    static byte[] load(long seq, int warehouse, int truckId, long orderId) {
        return APutOnTruck.newBuilder().setSeqnum(seq).setWhnum(warehouse).setTruckid(truckId).setShipid(orderId).build().toByteArray();
    }

    private static List<AProduct> things(List<Item> items) {
        return items.stream().map(item -> AProduct.newBuilder().setId(item.getProductId())
                .setDescription(item.getDescription()).setCount(item.getCount()).build()).toList();
    }
}
