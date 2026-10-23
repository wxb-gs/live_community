#!/bin/bash
# Initialize configuration and new indices without deleting existing data.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
bash "$SCRIPT_DIR/init-nacos-config.sh"
bash "$SCRIPT_DIR/init-es.sh"
if curl -sf "${GATEWAY_URL:-http://localhost:8080}/" >/dev/null; then
  python "$SCRIPT_DIR/init-test-data.py"
else
  echo "Gateway not ready; start applications after boundary migration, then run scripts/init-test-data.py."
fi
