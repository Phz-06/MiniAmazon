# MiniAmazon

An order-fulfilment backend built to stay correct when the systems it depends on do not behave.

Customers buy from a catalog; each paid order has to be packed in a warehouse, picked up by a
truck and delivered. The warehouse and the trucks live in an external "world simulator" that
drops a configurable share of the messages it receives and resends its own until they are
acknowledged. On top of that, services crash and Kafka redelivers. The goal of the design is
that every order is still fulfilled **exactly once**, and that this is demonstrated by tests
rather than claimed.

This is a rewrite of a two-person course project (Duke ECE 568). The original front end is by
Dongxin Tian.

## Architecture

```
                    HTTP                                   world simulator (not ours)
  browser  ------------------>  order-service                 +------------------+
                                  |        ^                  |  warehouse side  |
              warehouse.commands  |        |  warehouse.events|    port 23456    |
                                  v        |                  +------------------+
                              +----------------+   TCP, lossy          ^
                              | world-gateway  | ----------------------+
                              +----------------+
              carrier.commands    |        ^  carrier.events  +------------------+
                                  v        |                  |   carrier side   |
                              +----------------+   TCP, lossy |    port 12345    |
                              | carrier-service| -----------> +------------------+
                              +----------------+

  Kafka carries the four topics. Each service has its own Postgres database.
```

| Service | Owns | Why it is separate |
|---|---|---|
| `order-service` | customers, catalog, stock, orders, payments; the only HTTP API | Stateless; can run as many instances as the load needs |
| `world-gateway` | the one TCP connection to the warehouse side of the world | The world accepts a single warehouse connection per world, so this must be exactly one instance |
| `carrier-service` | trucks and shipments; the connection to the carrier side of the world | A different company with its own data; shares only the package id with the others |
| `shared` (library) | message contracts, outbox, idempotent consumer, reliable world link | Infrastructure every service needs in identical form |

## How an order is fulfilled

```
CREATED -> CONFIRMED -> PAID -> PACKING -> PACKED -> LOADING -> LOADED -> DELIVERING -> DELIVERED
           stock        |       gateway    carrier sends      gateway     carrier
           reserved     |       packs      a truck            loads       delivers
                        +-- payment and "please pack" are one commit
```

`order-service` decides, the other two do. Each step is triggered by an event from one of them
and answered with a command to the other.

What can go wrong, and what makes it harmless:

| Failure | Mechanism |
|---|---|
| A service commits and then crashes before publishing | **Transactional outbox**: the message is a row written in the same transaction as the state change; a relay publishes it and retries until Kafka has it |
| Kafka delivers a record twice (rebalance, relay retry) | **Idempotent consumer**: the handler and a "seen this message id" row are one transaction |
| The world drops a command | Commands stay pending and are resent with backoff until acknowledged; a resend carries the same sequence number, so the world executes it once |
| The world repeats a message | **Inbox**: applied together with a (world id, sequence number) row, acknowledged only after that commit |
| Two triggers race for the same order | Every status change is a **compare-and-set** `UPDATE ... WHERE status = :expected`; only the winner writes the next message |
| Steps of one order overtake each other | They cannot: the command for step n+1 does not exist until the event for step n was applied. Records of one order also share a Kafka key |
| A record can never be processed | Retried in place five times, then moved to `<topic>.DLT`. In place, because a retry topic would let later records of the same order overtake it |
| A service or Kafka restarts | Nothing needed for recovery is in memory. Each service rejoins its world and continues from its tables |
| The simulator restarts | It drops all its tables on startup, so the world is gone, and the next one gets the same id. The gateway is refused for 30 s, concludes the world is lost, creates a new one and announces it with a higher **generation**; `order-service` buys all stock again and the carrier registers its trucks again |
| Many buyers, few units | Stock is reserved with `UPDATE ... SET quantity = quantity - :n WHERE quantity >= :n`, rows locked in product-id order (no deadlocks), with a `CHECK (quantity >= 0)` behind it |
| Selling what the warehouse does not have | Stock is bought from the world and becomes sellable only when the world reports it arrived |

## Running it

Only Docker is required.

```bash
cp .env.example .env        # then fill in the three secrets
docker compose up --build
```

The shop is at http://localhost:8090. Nothing else needs to be started by hand: the gateway
creates a world, `order-service` buys the catalog stock in it, and the carrier registers its
trucks there. After a restart each service rejoins the same world. After a restart of the
simulator itself, allow about a minute: its world is gone and a new one has to be stocked.

`.env` is git-ignored. There are no default passwords and no default JWT key.

The world simulator is a closed-source x86-64 binary; on Apple Silicon it runs emulated. It also
simulates time: packing and driving take tens of seconds each, so an order needs a few minutes to
reach DELIVERED. `WORLD_SIM_SPEED` speeds that up, `WORLD_FLAKINESS` (0-99) sets how often the
world drops a message.

## Tests

```bash
./gradlew build
```

Integration tests start a real PostgreSQL and a real Kafka with Testcontainers, so Docker must be
running. The world is replaced by `FakeWorld`, which speaks the same protocol and is deliberately
hostile: it loses 30% of messages in each direction, resends everything unacknowledged every
25 ms, and has its connection cut every 150 ms.

| Test | What it shows |
|---|---|
| `InventoryConcurrencyIT` | 60 buyers for 20 units: exactly 20 win. Opposite lock orders never deadlock. A payment submitted 8 times is accepted once. Price edits and restocks during purchases lose nothing |
| `OrderFlowIT`, `SecurityIT` | The customer journey over HTTP and who may call what |
| `OutboxIT` | Eight relays polling at once never claim the same row |
| `FulfillmentMessagingIT` | `order-service` alone: every reply from the gateway and carrier arrives twice, plus a conflicting second truck; each order still produces one command per step. An unreadable record is dead-lettered and the records behind it still get through |
| `GatewayReliabilityIT` | Duplicate commands from Kafka and a hostile world: each command takes effect in the world once and is reported as one event |
| `GatewayWorldLossIT` | The world disappears and its replacement reuses the id and the message numbers: the gateway creates a new one, announces a new generation, and does not mistake new messages for repeats |
| `CarrierDispatchIT` | 18 packages, 3 trucks, same hostility: every package picked up and delivered once, trucks reused and never double-booked. Then the world is replaced by one with the same id and the carrier registers its trucks again |

Each of these was checked the other way round: with the mechanism it covers removed (lock
ordering, compare-and-set, inbox, retransmission, consumer de-duplication), the test fails.

### Against the real simulator

```bash
scripts/e2e.sh              # 15 orders, followed to DELIVERED
CHAOS=1 scripts/e2e.sh      # also SIGKILLs each service and restarts Kafka mid-flight
```

It exits non-zero unless every order was delivered once and the three service databases and the
simulator's own database agree (packages, stock totals, idle trucks, empty outboxes). Set
`WORLD_FLAKINESS=30` in `.env` to add message loss.

## Database schema

Each service owns a database on the shared Postgres server (`amazon`, `gateway`, `carrier`) and
its own Flyway migrations under `src/main/resources/db/migration`. Hibernate (order-service only)
runs with `ddl-auto=validate`.

## Known limits

*   One warehouse; an order is one package.
*   Orders that are in the warehouse or on a truck when the simulator restarts are lost with its
    world. They are counted and logged, not re-shipped, and so are orders taken in the 30 seconds
    before the gateway gives the old world up.
*   `world-gateway` and `carrier-service` are single instances by design. Nothing elects a leader;
    running two would be a deployment mistake that the world rejects at connect time.
*   A command the world refuses is reported (`CommandRejected`) and logged; the order then waits
    for a person. There is no automatic refund or cancellation.
*   Payment is a stand-in: it checks the amount and moves the status.
*   No schema registry; message compatibility relies on only ever adding protobuf fields.

## API Specifications for Frontend Development

This section details the exact API endpoints and data formats for frontend integration.

### 1. Authentication API

#### User Login

*   **Endpoint:** `POST /api/auth/login`
*   **Description:** Authenticates a user and returns a JWT.
*   **Request Body:**
    ```json
    {
      "username": "testuser",
      "password": "password123"
    }
    ```
*   **Response (Success - 200 OK):**
    ```json
    {
      "token": "eyJhbGciOiJIUzUxMiJ9...",
      "type": "Bearer"
    }
    ```
*   **Response (Error - 401 Unauthorized):** Authentication failure (invalid credentials)

#### User Signup

*   **Endpoint:** `POST /api/auth/signup`
*   **Description:** Registers a new user and returns a JWT for immediate authentication.
*   **Request Body:**
    ```json
    {
      "username": "newuser",
      "password": "password123",
      "email": "user@example.com",
      "firstName": "First",
      "lastName": "Last"
    }
    ```
*   **Response (Success - 200 OK):**
    ```json
    {
      "token": "eyJhbGciOiJIUzUxMiJ9...",
      "type": "Bearer"
    }
    ```
*   **Response (Error - 400 Bad Request):**
    *   Username exists: `"Error: Username already exists"`
    *   Email exists: `"Error: Email already exists"`
    *   Validation errors (e.g., invalid email format, password length)

#### Using JWT Authentication

*   For all protected endpoints, include the JWT in the `Authorization` header:
    ```
    Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
    ```

### 2. Order Management API

#### Create Order

*   **Endpoint:** `POST /api/orders/create`
*   **Description:** Creates a new order.
*   **Security:** Requires authentication (`isAuthenticated()`)
*   **Request Body:**
    ```json
    {
      "userId": 123,
      "items": [
        {
          "productId": "PROD-123",
          "quantity": 2
        },
        {
          "productId": "PROD-456",
          "quantity": 1
        }
      ],
      "deliveryX": 10,
      "deliveryY": 20,
      "upsAccountName": "UPS123456"  // Optional
    }
    ```
*   **Response (Success - 202 Accepted):**
    ```json
    {
      "orderId": "123",
      "status": "success"
    }
    ```

#### Confirm Order

*   **Endpoint:** `POST /api/orders/{orderId}/confirm`
*   **Description:** Confirms delivery location for an existing order.
*   **Security:** Requires authentication (`isAuthenticated()`)
*   **Path Variable:** `orderId` (Long)
*   **Request Body:**
    ```json
    {
      "orderId": 123,
      "x": 10,
      "y": 20
    }
    ```
*   **Response (Success - 202 Accepted):**
    ```json
    {
      "orderId": "123",
      "status": "confirmation_in_progress"
    }
    ```

#### Cancel Order

*   **Endpoint:** `POST /api/orders/{orderId}/cancel`
*   **Description:** Cancels an existing order.
*   **Security:** Requires authentication (`isAuthenticated()`)
*   **Path Variable:** `orderId` (Long)
*   **Response (Success - 202 Accepted):**
    ```json
    {
      "orderId": "123",
      "status": "cancellation_in_progress"
    }
    ```

#### Process Payment

*   **Endpoint:** `POST /api/orders/{orderId}/payment`
*   **Description:** Processes payment for an order.
*   **Security:** Requires authentication (`isAuthenticated()`)
*   **Path Variable:** `orderId` (Long)
*   **Request Body:**
    ```json
    {
      "orderId": 123,
      "creditCardNumber": "4111111111111111",
      "amount": 59.99
    }
    ```
*   **Response (Success - 202 Accepted):**
    ```json
    {
      "orderId": "123",
      "status": "payment_processing"
    }
    ```

#### Get Order Details

*   **Endpoint:** `GET /api/orders/{orderId}`
*   **Description:** Retrieves details for a specific order.
*   **Security:** Requires authentication (`isAuthenticated()`)
*   **Path Variable:** `orderId` (Long)
*   **Response (Success - 200 OK):**
    ```json
    {
      "id": 123,
      "orderItems": [
        {
          "id": 456,
          "quantity": 2,
          "price": 29.99,
          "product": {
            "id": 789,
            "name": "Product Name",
            "description": "Product Description"
          }
        }
      ],
      "deliveryX": 10,
      "deliveryY": 20,
      "status": "PROCESSING",
      "totalPrice": 59.98,
      "upsAccountName": "UPS123456"
    }
    ```

#### Get Order Status

*   **Endpoint:** `GET /api/orders/{orderId}/status`
*   **Description:** Gets the current status of an order.
*   **Security:** Requires authentication (`isAuthenticated()`)
*   **Path Variable:** `orderId` (Long)
*   **Response (Success - 200 OK):**
    ```json
    {
      "orderId": "123",
      "status": "PROCESSING"
    }
    ```

### 3. Warehouse API (Admin Only)

The admin account is created at first startup from `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`.

#### Order stock

*   **Endpoint:** `POST /api/world/buy`
*   **Query Parameters:** `productId` (Long), `description` (String), `count` (Integer),
    `warehouseId` (Integer, default 1)
*   **Response (200 OK):** `{"status": "success", "seqNum": 123}`
*   The stock becomes sellable when the warehouse reports it arrived.

#### Status

*   **Endpoint:** `GET /api/world/status`
*   **Response (200 OK):** `{"worldId": 1, "pendingMessages": 0}`

## Configuration

Every setting is read from environment variables; see `.env.example` and each service's
`src/main/resources/application.properties`.
