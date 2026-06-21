#!/bin/bash
# ============================================================
# Start infrastructure containers only (Docker)
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Starting infrastructure containers ==="
cd "$PROJECT_DIR"
docker compose up -d

echo ""
echo "Waiting for all infrastructure to be healthy..."
echo ""

# ---- nacos ----
echo -n "  nacos ... "
until curl -sf http://localhost:18848/nacos/v1/console/health/readiness > /dev/null 2>&1; do sleep 2; done
echo "OK"

# ---- mysql ----
echo -n "  mysql ... "
until docker exec mysql mysqladmin ping -h localhost -u root -proot --silent 2>/dev/null; do sleep 2; done
echo "OK"

# ---- redis ----
echo -n "  redis ... "
until docker exec redis redis-cli ping 2>/dev/null | grep -q PONG; do sleep 2; done
echo "OK"

# ---- cassandra ----
echo -n "  cassandra (may take ~60s) ... "
until docker exec cassandra cqlsh -e "DESCRIBE KEYSPACES" localhost 9042 > /dev/null 2>&1; do sleep 5; done
echo "OK"

# ---- minio ----
echo -n "  minio ... "
until curl -sf http://localhost:19000/minio/health/live > /dev/null 2>&1; do sleep 2; done
echo "OK"

# ---- kafka ----
echo -n "  kafka ... "
until docker exec kafka kafka-topics --bootstrap-server kafka:9092 --list > /dev/null 2>&1; do sleep 2; done
echo "OK"

# ---- elasticsearch ----
echo -n "  elasticsearch ... "
until curl -sf http://localhost:9200/_cluster/health > /dev/null 2>&1; do sleep 2; done
echo "OK"

# ---- canal-server ----
echo -n "  canal-server ... "
until curl -sf http://localhost:11111 > /dev/null 2>&1; do sleep 3; done
echo "OK"

echo ""
echo "=== All infrastructure is healthy ==="
docker compose ps
