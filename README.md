# MiniAmazon Backend

## Overview

This project is the backend system for a miniature e-commerce platform, mimicking some core functionalities of Amazon. It utilizes Spring Boot, Spring Security for authentication, JPA/Hibernate for database interaction, and PostgreSQL as the database. The system includes user registration, login, order placement, and administrative functions for interacting with an external world simulator.

## Technologies Used

*   **Java 21**, **Spring Boot 3.5**, **Spring Security 6** (JWT)
*   **Spring Data JPA / Hibernate**, **PostgreSQL 17**, **Flyway** migrations
*   **Protocol Buffers** over TCP to the world simulator
*   **Testcontainers** integration tests, **GitHub Actions** CI
*   **Docker Compose** for the whole stack

## Running it

Everything (database, app, world simulator) starts with one command. Only Docker is required.

```bash
cp .env.example .env        # then fill in the three secrets
docker compose up --build
```

The app is at http://localhost:8090. Log in with the admin account from `.env`, then
`POST /api/world/connect` to attach to the simulator.

`.env` is git-ignored. There are no default passwords and no default JWT key: the app refuses to
start without them, and no admin account is created unless `APP_ADMIN_PASSWORD` is set.

To run the app from an IDE against the Compose database, export the same variables
(`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `APP_JWT_SECRET`, ...) and run
`./gradlew bootRun`.

The world simulator is a closed-source x86-64 binary; on Apple Silicon it runs emulated.
`WORLD_FLAKINESS` (0-99) sets how often it drops a message.

## Tests

```bash
./gradlew test
```

The tests start a real PostgreSQL with Testcontainers, so Docker must be running. They cover the
customer journey over HTTP and what happens under concurrency:

*   60 buyers racing for 20 units: exactly 20 succeed, stock ends at 0.
*   Orders that lock the same products in opposite order never deadlock.
*   An order short on one line reserves nothing.
*   A payment submitted 8 times is accepted once; a cancel racing a payment has one winner.
*   Admin price edits and restocks during purchases never erase a stock movement.

## Database schema

The schema lives in `src/main/resources/db/migration` and changes only through new Flyway
migrations. Hibernate runs with `ddl-auto=validate`: it checks the entities against the schema
and never alters it.

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

### 3. World Simulator API (Admin Only)

These endpoints are only accessible to users with `ROLE_ADMIN`.

#### Admin Login
The admin account is created at first startup from `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`.

#### Connect to World

*   **Endpoint:** `POST /api/world/connect`
*   **Description:** Connects to the world simulator.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Query Parameters:**
    *   `worldId` (Long, Optional): ID of an existing world
    *   `host` (String, Optional, Default: "localhost"): Simulator hostname
    *   `port` (Integer, Optional, Default: 23456): Simulator port
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "connected",
      "worldId": 12345678
    }
    ```
*   **Response (Error - 400 Bad Request):**
    ```json
    {
      "status": "failed"
    }
    ```

#### Disconnect from World

*   **Endpoint:** `POST /api/world/disconnect`
*   **Description:** Disconnects from the world simulator.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "disconnected"
    }
    ```
*   **Response (Error - 400 Bad Request):**
    ```json
    {
      "status": "not connected"
    }
    ```

#### Set Simulation Speed

*   **Endpoint:** `POST /api/world/speed`
*   **Description:** Sets the world simulation speed.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Query Parameters:**
    *   `speed` (Integer, Required): The simulation speed value
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "success",
      "speed": 100
    }
    ```
*   **Response (Error - 400 Bad Request):**
    ```json
    {
      "status": "not connected"
    }
    ```

#### Buy Product

*   **Endpoint:** `POST /api/world/buy`
*   **Description:** Purchases products for the warehouse.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Query Parameters:**
    *   `warehouseId` (Integer, Required): Warehouse identifier
    *   `productId` (Long, Required): Product identifier
    *   `description` (String, Required): Product description
    *   `count` (Integer, Required): Quantity to purchase
    *   `price` (Long, Optional): Product price
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "success",
      "seqNum": 123
    }
    ```
*   **Response (Error - 400 Bad Request):**
    ```json
    {
      "status": "failed"
    }
    ```
    or
    ```json
    {
      "status": "not connected"
    }
    ```

#### Pack Order

*   **Endpoint:** `POST /api/world/pack`
*   **Description:** Packs an order with multiple products.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Query Parameters:**
    *   `warehouseId` (Integer, Required): Warehouse identifier
    *   `shipmentId` (Long, Required): Shipment identifier
*   **Request Body:** List of product arrays `[id, count, description]`
    ```json
    [
      [101, 2, "Product One"],
      [102, 1, "Product Two"]
    ]
    ```
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "success",
      "seqNum": 124
    }
    ```
*   **Response (Error - 400 Bad Request):** Same error formats as above

#### Load Package

*   **Endpoint:** `POST /api/world/load`
*   **Description:** Loads a package onto a truck.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Query Parameters:**
    *   `warehouseId` (Integer, Required): Warehouse identifier
    *   `truckId` (Integer, Required): Truck identifier
    *   `shipmentId` (Long, Required): Shipment identifier
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "success",
      "seqNum": 125
    }
    ```
*   **Response (Error - 400 Bad Request):** Same error formats as above

#### Query Package

*   **Endpoint:** `GET /api/world/package/{packageId}`
*   **Description:** Gets status of a specific package.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Path Variable:** `packageId` (Long)
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "success",
      "seqNum": 126
    }
    ```
*   **Response (Error - 400 Bad Request):** Same error formats as above

#### Get World ID

*   **Endpoint:** `GET /api/world/id`
*   **Description:** Gets the current world ID.
*   **Security:** Requires admin role (`hasRole('ADMIN')`)
*   **Response (Success - 200 OK):**
    ```json
    {
      "status": "success",
      "worldId": 12345678
    }
    ```
*   **Response (Error - 400 Bad Request):**
    ```json
    {
      "status": "not connected"
    }
    ```

## Database Schema (Key Entities)

*   **User:** Stores user information (id, username, password (hashed), email, name, role, enabled status). Has a one-to-many relationship with Orders.
*   **Order:** Stores order details (id, orderTime, status, totalAmount, deliveryX, deliveryY). Has a many-to-one relationship with User (contains `user_id` foreign key).
*   **OrderItem:** Stores items within an order (id, quantity, price, product reference).
*   **Product:** Stores product information (id, name, description, price).

## Configuration

All settings are in `src/main/resources/application.properties` and read their values from
environment variables; see `.env.example` for the full list.
