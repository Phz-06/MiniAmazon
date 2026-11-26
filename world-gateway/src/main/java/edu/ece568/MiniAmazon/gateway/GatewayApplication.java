package edu.ece568.MiniAmazon.gateway;

import edu.ece568.MiniAmazon.shared.messaging.SharedMessagingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The warehouse side of the world, as a service of its own.
 *
 * It is separate from order-service because of one constraint: the world
 * accepts a single warehouse connection per world. Whatever holds that socket
 * has to run as exactly one instance, while the service taking customer
 * orders should be free to run as many as the load needs. Kafka sits between
 * them, so either side can be down without the other losing anything.
 */
@SpringBootApplication
@Import(SharedMessagingConfig.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
