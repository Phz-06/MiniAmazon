-- carrier-service: a shipping company with a fleet of trucks.

CREATE TABLE world_session (
    id                smallint    PRIMARY KEY CONSTRAINT world_session_singleton CHECK (id = 1),
    world_id          bigint,
    generation        bigint      NOT NULL DEFAULT 0,
    -- The world refuses a second registration of the same truck ids, so we
    -- must remember whether it already happened for this world.
    trucks_registered boolean     NOT NULL DEFAULT false,
    updated_at        timestamptz NOT NULL DEFAULT now()
);
INSERT INTO world_session (id) VALUES (1);

CREATE TABLE truck (
    id     integer     PRIMARY KEY,
    status varchar(16) NOT NULL DEFAULT 'IDLE'
           CONSTRAINT truck_status_check CHECK (status IN ('IDLE', 'TO_WAREHOUSE', 'AT_WAREHOUSE', 'DELIVERING'))
);
-- Dispatch looks for "any idle truck"; only idle trucks are in this index.
CREATE INDEX truck_idle_idx ON truck (id) WHERE status = 'IDLE';

-- One row per package we were asked to carry. order_id is the package id
-- everywhere (ours, the warehouse's, the world's), and being the primary key
-- it makes a repeated pickup request a no-op.
CREATE TABLE shipment (
    order_id     bigint      PRIMARY KEY,
    warehouse_id integer     NOT NULL,
    dest_x       integer     NOT NULL,
    dest_y       integer     NOT NULL,
    truck_id     integer     CONSTRAINT shipment_truck_fk REFERENCES truck (id),
    status       varchar(20) NOT NULL DEFAULT 'REQUESTED'
                 CONSTRAINT shipment_status_check CHECK (status IN (
                     'REQUESTED', 'TRUCK_EN_ROUTE', 'TRUCK_ARRIVED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'ABANDONED')),
    requested_at timestamptz NOT NULL DEFAULT now(),
    delivered_at timestamptz
);
-- The queue of packages still waiting for a truck, oldest first.
CREATE INDEX shipment_waiting_idx ON shipment (requested_at, order_id) WHERE status = 'REQUESTED';
-- "Which shipment is this truck working on" when the world reports on a truck.
CREATE INDEX shipment_truck_idx ON shipment (truck_id) WHERE status IN ('TRUCK_EN_ROUTE', 'TRUCK_ARRIVED', 'OUT_FOR_DELIVERY');

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

