package edu.ece568.MiniAmazon.world;

import com.google.protobuf.InvalidProtocolBufferException;
import edu.ece568.MiniAmazon.messaging.OutboxMessage;
import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APack;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AProduct;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APurchaseMore;
import edu.ece568.MiniAmazon.proto.WorldAmazon.APutOnTruck;

import java.util.List;

/** The commands we send to the world, as they are stored in the outbox. */
public final class WorldCommands {
    public static final String PURCHASE = "PURCHASE";
    public static final String PACK = "PACK";
    public static final String LOAD = "LOAD";

    private WorldCommands() {
    }

    public static AProduct product(long id, String description, int count) {
        return AProduct.newBuilder().setId(id).setDescription(description).setCount(count).build();
    }

    public static byte[] purchase(long seqnum, int warehouse, List<AProduct> things) {
        return APurchaseMore.newBuilder().setSeqnum(seqnum).setWhnum(warehouse).addAllThings(things)
                .build().toByteArray();
    }

    public static byte[] pack(long seqnum, int warehouse, long shipId, List<AProduct> things) {
        return APack.newBuilder().setSeqnum(seqnum).setWhnum(warehouse).setShipid(shipId).addAllThings(things)
                .build().toByteArray();
    }

    public static byte[] load(long seqnum, int warehouse, int truckId, long shipId) {
        return APutOnTruck.newBuilder().setSeqnum(seqnum).setWhnum(warehouse).setTruckid(truckId).setShipid(shipId)
                .build().toByteArray();
    }

    /** Adds a stored command to a batch; one ACommands message can carry many. */
    static void addTo(ACommands.Builder batch, OutboxMessage message) throws InvalidProtocolBufferException {
        switch (message.type()) {
            case PURCHASE -> batch.addBuy(APurchaseMore.parseFrom(message.payload()));
            case PACK -> batch.addTopack(APack.parseFrom(message.payload()));
            case LOAD -> batch.addLoad(APutOnTruck.parseFrom(message.payload()));
            default -> throw new InvalidProtocolBufferException("Unknown world command type " + message.type());
        }
    }
}
