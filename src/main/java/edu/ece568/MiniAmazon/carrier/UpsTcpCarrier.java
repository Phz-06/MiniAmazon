package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.Amazon_UPS.LoadConfirmation;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.ShipmentRequest;
import edu.ece568.MiniAmazon.service.UPSService;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** The course's Amazon-UPS socket protocol, behind the carrier interface. */
@Component
public class UpsTcpCarrier implements CarrierPort {

    private final UPSService ups;

    public UpsTcpCarrier(UPSService ups) {
        this.ups = ups;
    }

    @Override
    public boolean isAvailable() {
        return ups.isConnected();
    }

    @Override
    public void requestPickup(ShipmentRequest request) throws IOException {
        long sent = ups.sendShipmentRequest(request.getPackageId(), request.getWarehouseId(),
                request.getDestination().getX(), request.getDestination().getY(), request.getUpsAccount());
        if (sent < 0) {
            throw new IOException("UPS did not accept the shipment request");
        }
    }

    @Override
    public void confirmLoaded(LoadConfirmation confirmation) throws IOException {
        if (ups.sendLoadConfirmation(confirmation.getPackageId(), confirmation.getTruckId()) < 0) {
            throw new IOException("UPS did not accept the load confirmation");
        }
    }
}
