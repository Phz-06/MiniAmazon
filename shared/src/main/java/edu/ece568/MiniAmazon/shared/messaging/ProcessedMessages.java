package edu.ece568.MiniAmazon.shared.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Which Kafka messages this service has already applied. */
public class ProcessedMessages {

    private final JdbcClient jdbc;

    public ProcessedMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * True the first time an id is seen. Must share a transaction with the
     * message's effect, so the two commit or roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String messageId) {
        return jdbc.sql("INSERT INTO processed_message (message_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", messageId).update() == 1;
    }
}
