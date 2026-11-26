package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.shared.messaging.SharedMessagingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The shipping company. It owns the trucks: it decides which truck fetches
 * which package, drives them through the world, and reports back.
 *
 * It knows nothing about orders, customers or stock. All it shares with
 * order-service is the package id and four message types on Kafka.
 */
@SpringBootApplication
@Import(SharedMessagingConfig.class)
public class CarrierApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarrierApplication.class, args);
    }
}
