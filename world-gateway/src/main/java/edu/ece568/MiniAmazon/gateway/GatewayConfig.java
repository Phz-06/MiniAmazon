package edu.ece568.MiniAmazon.gateway;

import edu.ece568.MiniAmazon.proto.WorldAmazon.ACommands;
import edu.ece568.MiniAmazon.proto.WorldAmazon.AResponses;
import edu.ece568.MiniAmazon.shared.worldlink.WorldCommandStore;
import edu.ece568.MiniAmazon.shared.worldlink.WorldInbox;
import edu.ece568.MiniAmazon.shared.worldlink.WorldLink;
import edu.ece568.MiniAmazon.shared.worldlink.WorldSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class GatewayConfig {

    @Bean
    WorldCommandStore worldCommandStore(JdbcClient jdbc, @Value("${app.world.retry-base-ms:1000}") long retryBaseMs) {
        return new WorldCommandStore(jdbc, retryBaseMs);
    }

    @Bean
    WorldInbox worldInbox(JdbcClient jdbc) {
        return new WorldInbox(jdbc);
    }

    @Bean
    WorldSession worldSession(JdbcClient jdbc) {
        return new WorldSession(jdbc);
    }

    @Bean(destroyMethod = "close")
    WorldLink<ACommands, AResponses> worldLink(@Value("${world.host}") String host,
                                               @Value("${world.port}") int port,
                                               @Value("${app.world.connect-timeout-ms:5000}") int timeoutMs,
                                               WorldCommandStore commands, WorldEventPublisher listener) {
        return new WorldLink<>("warehouse", host, port, timeoutMs, new AmazonProtocol(), commands, listener);
    }
}
