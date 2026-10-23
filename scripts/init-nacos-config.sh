#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NACOS_URL="http://${NACOS_HOST:-localhost}:${NACOS_PORT:-18848}"
# Retire old shared credentials without deleting the shared config reference.
curl -fsS -X POST "$NACOS_URL/nacos/v1/cs/configs" --data-urlencode 'dataId=shared-infra.yaml' --data-urlencode 'group=COMMON' --data-urlencode 'content=# Infrastructure is configured per service; no global credentials.' --data-urlencode 'type=yaml' >/dev/null
for service in gateway auth-service note-service upload-service leaf-service search-service search-sync-service; do
  module_path=$(python "$SCRIPT_DIR/project_layout.py" --service-path "$service")
  # Remove spring.config.import to avoid self-referencing config imports.
  content=$(awk '/^  config:/{skip=1;next} skip && /^  [a-zA-Z]/{skip=0} !skip{print}' "$SCRIPT_DIR/../$module_path/src/main/resources/application.yml")
  curl -fsS -X POST "$NACOS_URL/nacos/v1/cs/configs" --data-urlencode "dataId=$service.yaml" --data-urlencode 'group=DEFAULT_GROUP' --data-urlencode "content=$content" --data-urlencode 'type=yaml' >/dev/null
  echo "$service.yaml updated"
done
