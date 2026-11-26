-- order-service no longer talks to the world itself. It publishes commands to
-- Kafka and reacts to events; the connection, its sequence numbers and its
-- inbox moved to the world-gateway's own database.

DROP TABLE world_inbox;
DROP TABLE outbox_message;
DROP SEQUENCE outbox_message_seq;

-- We still remember which world our stock figures describe: a different world
-- means an empty warehouse and everything has to be bought again.
ALTER TABLE world_session DROP COLUMN auto_connect;
-- The simulator reuses world ids after a restart, so worlds are told apart by
-- the gateway's generation counter, not by id.
ALTER TABLE world_session ADD COLUMN generation bigint NOT NULL DEFAULT 0;

-- Transactional outbox for Kafka (see EventOutbox).
CREATE SEQUENCE event_outbox_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE event_outbox (
    id              bigint       PRIMARY KEY,
    topic           varchar(64)  NOT NULL,
    msg_key         varchar(64)  NOT NULL,
    payload         bytea        NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING'
                    CONSTRAINT event_outbox_status_check CHECK (status IN ('PENDING', 'DONE')),
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    completed_at    timestamptz
);
-- Only pending rows are indexed: the relay's poll stays cheap as history grows.
CREATE INDEX event_outbox_due_idx ON event_outbox (next_attempt_at) WHERE status = 'PENDING';

-- Ids of Kafka messages already applied (see IdempotentConsumer).
CREATE TABLE processed_message (
    message_id   varchar(64) PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);
