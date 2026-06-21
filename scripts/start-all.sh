#!/bin/bash
# ============================================================
# Start everything: Docker infra + all Java services locally
#
# Usage:
#   ./scripts/start-all.sh            # start all
#   ./scripts/start-all.sh --no-build # skip mvn compile
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD=true
INFRA_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=false ;;
    --infra-only) INFRA_ONLY=true ;;
  esac
done

cd "$PROJECT_DIR"

# ============================================================
# 1. Infrastructure
# ============================================================
echo ""
echo "============================================================"
echo "  STEP 1/4: Starting infrastructure (Docker)"
echo "============================================================"
bash "$SCRIPT_DIR/start-infra.sh"

# ============================================================
# 2. Build
# ============================================================
if $BUILD; then
  echo ""
  echo "============================================================"
  echo "  STEP 2/4: Building all modules"
  echo "============================================================"
  mvn compile -q
  echo "Build done."
else
  echo ""
  echo "  (skipping build — --no-build flag)"
fi

if $INFRA_ONLY; then
  echo ""
  echo "=== Infrastructure only — done ==="
  exit 0
fi

# ============================================================
# 3. Init ES indices (first time only, idempotent)
# ============================================================
echo ""
echo "============================================================"
echo "  STEP 3/4: Initializing ES indices"
echo "============================================================"
bash "$SCRIPT_DIR/init-es.sh" 2>/dev/null || echo "  (indices may already exist — ignoring error)"

# ============================================================
# 4. Start Java services (in dependency order)
# ============================================================
echo ""
echo "============================================================"
echo "  STEP 4/4: Starting Java services"
echo "============================================================"

LOGDIR="$PROJECT_DIR/logs"
mkdir -p "$LOGDIR"

# Helper: start a Spring Boot service in background, log to file
run_service() {
  local module=$1
  local name=$2
  echo "  starting $name ($module) ..."
  nohup mvn -pl "$module" spring-boot:run -q > "$LOGDIR/$name.log" 2>&1 &
  echo $! > "$LOGDIR/$name.pid"
  echo "    pid=$(cat $LOGDIR/$name.pid)  log=$LOGDIR/$name.log"
}

echo ""
echo "--- Tier 1: ID & infrastructure services ---"
run_service "leaf-service"    "leaf-service"
sleep 8

echo ""
echo "--- Tier 2: Provider services ---"
run_service "upload-service"  "upload-service"
run_service "auth-service"    "auth-service"
run_service "note-service"    "note-service"
sleep 10

echo ""
echo "--- Tier 3: Search services ---"
run_service "search-sync-service" "search-sync-service"
run_service "search-service"      "search-service"
sleep 8

echo ""
echo "--- Tier 4: Gateway ---"
run_service "gateway"         "gateway"
sleep 5

echo ""
echo "============================================================"
echo "  All services started!"
echo "============================================================"
echo ""
echo "  Logs:    $LOGDIR/"
echo "  PIDs:    $LOGDIR/*.pid"
echo ""
echo "  Ports:"
echo "    gateway              http://localhost:8080"
echo "    upload-service       http://localhost:8081"
echo "    leaf-service         http://localhost:8082"
echo "    note-service         http://localhost:8083"
echo "    auth-service         http://localhost:8084"
echo "    search-sync-service  http://localhost:8085"
echo "    search-service       http://localhost:8086"
echo ""
echo "  Infrastructure:"
echo "    Nacos      http://localhost:18848"
echo "    MySQL      localhost:13306"
echo "    Redis      localhost:16379"
echo "    MinIO      http://localhost:19000 (console :19001)"
echo "    Cassandra  localhost:19042"
echo "    Kafka      localhost:9092"
echo "    ES         http://localhost:9200"
echo "    Canal      localhost:11111"
echo ""
echo "  Frontend (Vite):  cd front && npm run dev"
echo ""
echo "  Stop all:  bash $SCRIPT_DIR/stop-all.sh"
