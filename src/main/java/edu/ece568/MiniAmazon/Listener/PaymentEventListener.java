package edu.ece568.MiniAmazon.Listener;

import edu.ece568.MiniAmazon.Event.ProcessPaymentEvent;
import edu.ece568.MiniAmazon.service.OrderService;
import edu.ece568.MiniAmazon.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;

@Slf4j
@Service
public class PaymentEventListener {

    private final PaymentService paymentService;

    public PaymentEventListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @EventListener
    public void handlePaymentEvent(ProcessPaymentEvent event) throws AccessDeniedException {
        log.info("Received PaymentEvent: {} - {}$", event.getOrderId(), event.getAmount());
        paymentService.processPayment(event.getOrderId());
        log.info("Payment processed for order id {}", event.getOrderId());
    }
}
