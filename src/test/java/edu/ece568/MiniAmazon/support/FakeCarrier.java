package edu.ece568.MiniAmazon.support;

import edu.ece568.MiniAmazon.carrier.CarrierPort;
import edu.ece568.MiniAmazon.fulfillment.FulfillmentService;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.LoadConfirmation;
import edu.ece568.MiniAmazon.proto.Amazon_UPS.ShipmentRequest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A shipping company that is unreliable in the ways a real one is: it refuses
 * one request in five, and when it does answer, it answers twice (the second
 * truck assignment even names a different truck).
 */
@TestComponent
@Primary
public class FakeCarrier implements CarrierPort {

    private final FulfillmentService fulfillment;
    private final ExecutorService callbacks = Executors.newFixedThreadPool(4);
    private final Random random = new Random(11);
    private final AtomicInteger trucks = new AtomicInteger();
    private final AtomicInteger refusals = new AtomicInteger();
    private final Map<Long, Integer> firstTruck = new ConcurrentHashMap<>();

    public FakeCarrier(@Lazy FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void requestPickup(ShipmentRequest request) throws IOException {
        refuseSometimes();
        long orderId = Long.parseLong(request.getPackageId());
        int truck = firstTruck.computeIfAbsent(orderId, id -> trucks.incrementAndGet());
        callbacks.submit(() -> {
            fulfillment.onTruckAssigned(orderId, truck);
            fulfillment.onTruckAssigned(orderId, truck + 1000);
        });
    }

    @Override
    public void confirmLoaded(LoadConfirmation confirmation) throws IOException {
        refuseSometimes();
        long orderId = Long.parseLong(confirmation.getPackageId());
        callbacks.submit(() -> {
            fulfillment.onOutForDelivery(orderId);
            fulfillment.onDelivered(orderId);
            fulfillment.onDelivered(orderId);
        });
    }

    public int truckFor(long orderId) {
        return firstTruck.getOrDefault(orderId, -1);
    }

    public int refusals() {
        return refusals.get();
    }

    private void refuseSometimes() throws IOException {
        if (random.nextDouble() < 0.2) {
            refusals.incrementAndGet();
            throw new IOException("carrier is busy");
        }
    }
}
