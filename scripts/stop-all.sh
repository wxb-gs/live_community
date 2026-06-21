#!/bin/bash
# ============================================================
# Stop all locally-running Java services + Docker infra
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LOGDIR="$PROJECT_DIR/logs"

echo "=== Stopping Java services ==="

stop_by_pid() {
  local name=$1
  local pidfile="$LOGDIR/$name.pid"
  if [ -f "$pidfile" ]; then
    local pid=$(cat "$pidfile")
    if kill -0 "$pid" 2>/dev/null; then
      echo "  stopping $name (pid=$pid) ..."
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pidfile"
  fi
}

# Stop in reverse order
stop_by_pid "gateway"
stop_by_pid "search-service"
stop_by_pid "search-sync-service"
stop_by_pid "note-service"
stop_by_pid "auth-service"
stop_by_pid "upload-service"
stop_by_pid "leaf-service"

echo ""
echo "=== Stopping Docker infrastructure ==="
cd "$PROJECT_DIR"
docker compose down

echo ""
echo "=== All stopped ==="
