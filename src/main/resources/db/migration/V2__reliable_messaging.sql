-- Everything the application needs to survive a restart or a lost message
-- now lives in the database instead of in memory.

-- Which world we belong to. One row, enforced.
CREATE TABLE world_session (
    id           smallint    PRIMARY KEY CONSTRAINT world_session_singleton CHECK (id = 1),
    world_id     bigint,
    -- true = an admin asked to be connected; reconnect on our own after a
    -- crash or a dropped socket.
    auto_connect boolean     NOT NULL DEFAULT false,
    updated_at   timestamptz NOT NULL DEFAULT now()
);
INSERT INTO world_session (id) VALUES (1);

-- Transactional outbox. A row is inserted in the same transaction as the
-- state change that caused it, so "order is PACKING" and "a pack command
-- exists" commit or roll back together. A relay sends rows until the other
-- side acknowledges them.
--
-- For the world, the row id is the protocol's sequence number: it is unique,
-- survives restarts, and a resend carries the same number as the original.
CREATE SEQUENCE outbox_message_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE outbox_message (
    id              bigint       PRIMARY KEY,
    destination     varchar(16)  NOT NULL
                    CONSTRAINT outbox_destination_check CHECK (destination IN ('WORLD', 'CARRIER')),
    type            varchar(32)  NOT NULL,
    order_id        bigint       CONSTRAINT outbox_order_fk REFERENCES customer_order (id),
    payload         bytea        NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING'
                    CONSTRAINT outbox_status_check CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    last_error      text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    completed_at    timestamptz
);
-- The relay polls for "pending and due" several times a second. A partial
-- index holds only pending rows, so the poll stays cheap however many
-- finished messages pile up in the table.
CREATE INDEX outbox_due_idx ON outbox_message (destination, next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX outbox_order_idx ON outbox_message (order_id);

-- Inbox. The world resends a message until we acknowledge it, so the same
-- "packed" or "arrived" can show up many times. The first delivery inserts
-- a row here in the same transaction as its effect; every later delivery
-- hits the primary key and is acknowledged without being applied again.
CREATE TABLE world_inbox (
    world_id    bigint      NOT NULL,
    seqnum      bigint      NOT NULL,
    kind        varchar(16) NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (world_id, seqnum)
);

-- A shipment is identified by its order id from now on.
ALTER TABLE customer_order DROP COLUMN shipment_id;
DROP TABLE amazon_package;
DROP SEQUENCE amazon_package_seq;
