-- world-gateway owns the connection to the warehouse side of the world and
-- everything needed to resume it after a crash.

CREATE TABLE world_session (
    id         smallint    PRIMARY KEY CONSTRAINT world_session_singleton CHECK (id = 1),
    world_id   bigint,
    generation bigint      NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO world_session (id) VALUES (1);

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
CREATE INDEX event_outbox_due_idx ON event_outbox (next_attempt_at) WHERE status = 'PENDING';

-- Ids of Kafka messages already applied (see IdempotentConsumer).
CREATE TABLE processed_message (
    message_id   varchar(64) PRIMARY KEY,
    processed_at timestamptz NOT NULL DEFAULT now()
);

-- Commands to the world, pending until the world acknowledges them
-- (see WorldCommandStore). The id is the protocol sequence number.
CREATE SEQUENCE world_command_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE world_command (
    id              bigint       PRIMARY KEY,
    type            varchar(32)  NOT NULL,
    ref_id          bigint,
    payload         bytea        NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING'
                    CONSTRAINT world_command_status_check CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    last_error      text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    completed_at    timestamptz
);
CREATE INDEX world_command_due_idx ON world_command (next_attempt_at) WHERE status = 'PENDING';

-- Messages from the world already applied (see WorldInbox).
CREATE TABLE world_inbox (
    world_id    bigint      NOT NULL,
    seqnum      bigint      NOT NULL,
    kind        varchar(16) NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (world_id, seqnum)
);

