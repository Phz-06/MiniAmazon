#!/bin/bash
# End-to-end check against the running Compose stack and the real world simulator.
#
#   scripts/e2e.sh            place 15 orders and follow them to DELIVERED
#   ORDERS=30 scripts/e2e.sh  more orders
#   CHAOS=1 scripts/e2e.sh    also SIGKILL each service and restart Kafka while orders are in flight
#
# Run it with WORLD_FLAKINESS=30 in .env to add message loss. It exits non-zero
# unless every order was delivered exactly once and the three databases and the
# world's own database agree with each other.
set -u
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a

ORDERS=${ORDERS:-15}
CHAOS=${CHAOS:-0}
URL=http://127.0.0.1:${APP_PORT:-8090}
JSON='Content-Type: application/json'

sql()   { docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$1" -At -c "$2" | tr '\n' ' ' | sed 's/ $//'; }
world() { docker compose exec -T world-db psql -U postgres -d worldSim -At -c "$1" | tr '\n' ' ' | sed 's/ $//'; }
field() { python3 -c "import sys,json;print(json.load(sys.stdin)['$1'])"; }
wait_http() { for _ in $(seq 1 90); do [ "$(curl -s -o /dev/null -w '%{http_code}' "$URL/api/products")" = 200 ] && return 0; sleep 2; done; return 1; }
kill_and_restart() { echo ">>> SIGKILL $1"; docker compose kill "$1" > /dev/null 2>&1; sleep "${2:-3}"; docker compose up -d "$1" > /dev/null 2>&1; }

wait_http || { echo "order-service is not answering on $URL"; exit 1; }
# Ready means: all three services are in the same generation of the world (after a simulator
# restart the gateway needs about 30 s to conclude its world is gone and create a new one), and
# the stock ordered for that world has arrived.
echo "waiting for the services to agree on a world and for its stock to arrive..."
for _ in $(seq 1 150); do
  GEN=$(sql gateway "select generation from world_session")
  if [ "${GEN:-0}" -ge 1 ] 2>/dev/null \
     && [ "$(sql amazon "select generation from world_session")" = "$GEN" ] \
     && [ "$(sql carrier "select generation from world_session where trucks_registered")" = "$GEN" ] \
     && [ "$(sql gateway "select count(*) from world_command where status = 'PENDING'")" = 0 ] \
     && [ "$(sql amazon "select count(*) from event_outbox where status = 'PENDING'")" = 0 ] \
     && [ "$(sql amazon "select count(*) from product where quantity > 0")" -ge 50 ]; then break; fi
  sleep 2
done
BEFORE=$(sql amazon "select coalesce(max(id), 0) from customer_order")

USER="e2e$(date +%s)"
TOKEN=$(curl -s -H "$JSON" -d "{\"username\":\"$USER\",\"password\":\"password123\",\"email\":\"$USER@example.com\"}" "$URL/api/auth/signup" | field token)
AUTH="Authorization: Bearer $TOKEN"

echo "placing $ORDERS orders (chaos=$CHAOS, flakiness=${WORLD_FLAKINESS:-0})"
for n in $(seq 1 "$ORDERS"); do
  curl -s -o /dev/null -H "$AUTH" -H "$JSON" -d "{\"productId\":\"$((n % 5))\",\"quantity\":2}" "$URL/api/cart/add"
  CHECKOUT=$(curl -s -H "$AUTH" -H "$JSON" -d "{\"deliveryX\":$((n + 3)),\"deliveryY\":$((n + 5))}" "$URL/api/cart/checkout")
  ID=$(echo "$CHECKOUT" | field orderId); TOTAL=$(echo "$CHECKOUT" | field totalPrice)
  curl -s -o /dev/null -H "$AUTH" -H "$JSON" -d "{\"orderId\":$ID,\"x\":$((n + 3)),\"y\":$((n + 5))}" "$URL/api/orders/$ID/confirm"
  curl -s -o /dev/null -H "$AUTH" -H "$JSON" -d "{\"orderId\":$ID,\"creditCardNumber\":\"4111111111111111\",\"amount\":\"$TOTAL\"}" "$URL/api/orders/$ID/payment"
  if [ "$CHAOS" = 1 ] && [ "$n" -eq $((ORDERS / 2)) ]; then kill_and_restart world-gateway; fi
done

MINE="id > $BEFORE"
if [ "$CHAOS" = 1 ]; then
  sleep 10; kill_and_restart order-service; wait_http
  sleep 15; kill_and_restart carrier-service
  sleep 15; echo ">>> restarting Kafka"; docker compose restart kafka > /dev/null 2>&1
  sleep 20; kill_and_restart world-gateway 5
fi

for i in $(seq 1 180); do
  STATUS=$(sql amazon "select status || ':' || count(*) from customer_order where $MINE group by status order by status")
  echo "t+$((i * 5))s  $STATUS"
  [ "$STATUS" = "DELIVERED:$ORDERS" ] && break
  sleep 5
done

FAIL=0
check() { if [ "$2" = "$3" ]; then echo "ok    $1: $2"; else echo "FAIL  $1: got '$2', expected '$3'"; FAIL=1; fi; }
WORLD_ID=$(sql gateway "select world_id from world_session")
check "orders delivered"                         "$(sql amazon "select count(*) from customer_order where $MINE and status = 'DELIVERED'")" "$ORDERS"
check "packages in the world for these orders"   "$(world "select count(*) from package where world_id = $WORLD_ID and package_id > $BEFORE")" "$ORDERS"
check "of those, delivered"                      "$(world "select count(*) from package where world_id = $WORLD_ID and package_id > $BEFORE and package_status = 'DELIVERED'")" "$ORDERS"
check "carrier shipments delivered"              "$(sql carrier "select count(*) from shipment where order_id > $BEFORE and status = 'DELIVERED'")" "$ORDERS"
check "trucks idle again"                        "$(sql carrier "select count(*) from truck where status <> 'IDLE'")" "0"
check "our stock equals the world's stock"       "$(sql amazon "select sum(quantity) from product")" "$(world "select sum(count) from product where world_id = $WORLD_ID")"
check "warehouse commands the world refused"     "$(sql gateway "select count(*) from world_command where status = 'FAILED' and last_error not like 'Superseded%'")" "0"
check "carrier commands the world refused"       "$(sql carrier "select count(*) from world_command where status = 'FAILED' and last_error not like 'Superseded%'")" "0"
check "messages still waiting in any outbox"     "$(sql amazon "select count(*) from event_outbox where status = 'PENDING'")/$(sql gateway "select count(*) from event_outbox where status = 'PENDING'")/$(sql carrier "select count(*) from event_outbox where status = 'PENDING'")" "0/0/0"
exit $FAIL
