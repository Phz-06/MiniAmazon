package edu.ece568.MiniAmazon.carrier;

import edu.ece568.MiniAmazon.proto.WorldUps.UCommands;
import edu.ece568.MiniAmazon.proto.WorldUps.UResponses;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import edu.ece568.MiniAmazon.shared.worldlink.WorldInbox;
import edu.ece568.MiniAmazon.shared.worldlink.WorldLink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class CarrierConfig {

    @Bean
    WorldCommandStore worldCommandStore(JdbcClient jdbc, @Value("${app.world.retry-base-ms:1000}") long retryBaseMs) {
        return new WorldCommandStore(jdbc, retryBaseMs);
    }

    @Bean
    WorldInbox worldInbox(JdbcClient jdbc) {
        return new WorldInbox(jdbc);
    }

    @Bean(destroyMethod = "close")
    WorldLink<UCommands, UResponses> worldLink(@Value("${world.host}") String host,
                                               @Value("${world.port}") int port,
                                               @Value("${app.world.connect-timeout-ms:5000}") int timeoutMs,
                                               @Value("${app.carrier.trucks:10}") int trucks,
                                               WorldCommandStore commands, UpsResponseHandler listener) {
        return new WorldLink<>("carrier", host, port, timeoutMs, new UpsProtocol(trucks), commands, listener);
    }
}
