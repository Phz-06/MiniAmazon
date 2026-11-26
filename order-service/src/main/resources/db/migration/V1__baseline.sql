-- Baseline schema. From here on the schema changes only through new
-- V<n>__*.sql files; Hibernate runs with ddl-auto=validate and never alters it.
--
-- Sequences step by 50 because Hibernate allocates ids in blocks of 50 and
-- only goes back to the database once per block.

CREATE SEQUENCE users_seq          START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE ware_house_seq     START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE product_seq        START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE shopping_cart_seq  START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE cart_item_seq      START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE customer_order_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE order_item_seq     START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE amazon_package_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE users (
    id              bigint       PRIMARY KEY,
    username        varchar(255) NOT NULL CONSTRAINT users_username_key UNIQUE,
    email           varchar(255) NOT NULL CONSTRAINT users_email_key UNIQUE,
    password        varchar(255) NOT NULL,
    first_name      varchar(255),
    last_name       varchar(255),
    -- Stored by name: reordering the Java enum must not silently change roles.
    role            varchar(255) NOT NULL
                    CONSTRAINT users_role_check CHECK (role IN ('USER', 'STAFF', 'ADMIN')),
    account_enabled boolean      NOT NULL,
    created_at      timestamp(6),
    last_login_at   timestamp(6)
);

CREATE TABLE ware_house (
    id           bigint  PRIMARY KEY,
    name         varchar(255),
    x_coordinate integer NOT NULL,
    y_coordinate integer NOT NULL
);

CREATE TABLE product (
    id            bigint        PRIMARY KEY,
    product_id    varchar(255)  NOT NULL CONSTRAINT product_product_id_key UNIQUE,
    name          varchar(255)  NOT NULL,
    description   varchar(255),
    category      varchar(255),
    price         numeric(12,2) NOT NULL CONSTRAINT product_price_check CHECK (price >= 0),
    -- Last line of defence against overselling. The application reserves stock
    -- with "UPDATE ... WHERE quantity >= :n"; if any code path ever forgets
    -- that guard, the database rejects the write instead of going negative.
    quantity      integer       NOT NULL CONSTRAINT product_quantity_check CHECK (quantity >= 0),
    ware_house_id bigint        CONSTRAINT product_ware_house_fk REFERENCES ware_house (id)
);
-- Catalog pages filter by category.
CREATE INDEX product_category_idx ON product (category);

CREATE TABLE shopping_cart (
    id             bigint        PRIMARY KEY,
    user_id        bigint        NOT NULL CONSTRAINT shopping_cart_user_id_key UNIQUE
                                 CONSTRAINT shopping_cart_user_fk REFERENCES users (id),
    total_price    numeric(12,2),
    total_quantity integer       NOT NULL
);

CREATE TABLE cart_item (
    id         bigint        PRIMARY KEY,
    cart_id    bigint        NOT NULL CONSTRAINT cart_item_cart_fk REFERENCES shopping_cart (id),
    product_id bigint        NOT NULL CONSTRAINT cart_item_product_fk REFERENCES product (id),
    quantity   integer       NOT NULL CONSTRAINT cart_item_quantity_check CHECK (quantity > 0),
    price      numeric(12,2)
);
-- Postgres indexes primary keys and unique constraints but not foreign keys.
-- Every cart read is "lines of this cart"; without this it is a full scan.
CREATE INDEX cart_item_cart_idx ON cart_item (cart_id);

CREATE TABLE customer_order (
    id               bigint        PRIMARY KEY,
    user_id          bigint        NOT NULL CONSTRAINT customer_order_user_fk REFERENCES users (id),
    status           varchar(255)  NOT NULL
                     CONSTRAINT customer_order_status_check CHECK (status IN (
                         'CREATED', 'CONFIRMED', 'PAID', 'PACKING', 'PACKED',
                         'LOADING', 'LOADED', 'DELIVERING', 'DELIVERED', 'CANCELED')),
    total_price      numeric(12,2) NOT NULL,
    created_at       timestamp(6)  NOT NULL,
    deliveryx        integer       NOT NULL,
    deliveryy        integer       NOT NULL,
    ups_account_name varchar(255),
    shipment_id      varchar(255)  CONSTRAINT customer_order_shipment_id_key UNIQUE,
    truck_id         integer
);
-- "My orders", newest first: one index serves both the filter and the sort.
CREATE INDEX customer_order_user_created_idx ON customer_order (user_id, created_at DESC);

CREATE TABLE order_item (
    id         bigint        PRIMARY KEY,
    order_id   bigint        NOT NULL CONSTRAINT order_item_order_fk REFERENCES customer_order (id),
    product_id bigint        NOT NULL CONSTRAINT order_item_product_fk REFERENCES product (id),
    quantity   integer       NOT NULL CONSTRAINT order_item_quantity_check CHECK (quantity > 0),
    -- Unit price when the order was placed.
    price      numeric(12,2) NOT NULL
);
CREATE INDEX order_item_order_idx ON order_item (order_id);

CREATE TABLE amazon_package (
    id         bigint       PRIMARY KEY,
    order_id   bigint       CONSTRAINT amazon_package_order_id_key UNIQUE
                            CONSTRAINT amazon_package_order_fk REFERENCES customer_order (id),
    shipmentid varchar(255) CONSTRAINT amazon_package_shipmentid_key UNIQUE,
    status     varchar(255)
);
