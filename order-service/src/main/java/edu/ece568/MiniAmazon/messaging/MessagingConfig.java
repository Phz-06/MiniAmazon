package edu.ece568.MiniAmazon.messaging;

import edu.ece568.MiniAmazon.shared.messaging.SharedMessagingConfig;
import edu.ece568.MiniAmazon.shared.worldlink.WorldSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
@Import(SharedMessagingConfig.class)
public class MessagingConfig {

    @Bean
    WorldSession worldSession(JdbcClient jdbc) {
        return new WorldSession(jdbc);
    }
}
