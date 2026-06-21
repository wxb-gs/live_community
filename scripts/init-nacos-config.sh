#!/bin/bash
# ============================================================
# Initialize Nacos Config — upload shared & service configs
#
# Usage:
#   ./scripts/init-nacos-config.sh
#
# Prerequisites:
#   Nacos must be running on ${NACOS_HOST:-localhost}:18848
# ============================================================
set -e

NACOS_HOST="${NACOS_HOST:-localhost}"
NACOS_PORT="${NACOS_PORT:-18848}"
NACOS_URL="http://${NACOS_HOST}:${NACOS_PORT}"

echo "=== Initializing Nacos Config at ${NACOS_URL} ==="
echo ""

# ---- shared-infra.yaml (所有服务共享) ----
SHARED_INFRA=$(cat <<'YAML'
# Shared infrastructure configuration — imported by all services
# spring.config.import: optional:nacos:shared-infra.yaml?group=COMMON&refreshEnabled=true

spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_HOST:localhost}:18848
        enabled: true
      config:
        server-addr: ${NACOS_HOST:localhost}:18848
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:16379}
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:13306}/live_community?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}

dubbo:
  registry:
    address: nacos://${NACOS_HOST:localhost}:18848

elasticsearch:
  host: ${ES_HOST:localhost}
  port: ${ES_PORT:9200}

minio:
  endpoint: http://${MINIO_HOST:localhost}:${MINIO_PORT:19000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}

kafka:
  bootstrap-servers: ${KAFKA_HOST:localhost}:${KAFKA_PORT:9092}
YAML
)

# ---- Post shared-infra.yaml to Nacos ----
echo "  Uploading shared-infra.yaml (group=COMMON) ..."
curl -sf -X POST "${NACOS_URL}/nacos/v1/cs/configs" \
  -d "dataId=shared-infra.yaml" \
  -d "group=COMMON" \
  -d "content=${SHARED_INFRA}" \
  -d "type=yaml" \
  > /dev/null
echo "  OK"

# ---- Per-service configs ----
echo ""
echo "  Uploading per-service configs ..."

upload_config() {
  local dataId=$1
  local content=$2
  local group=${3:-DEFAULT_GROUP}

  curl -sf -X POST "${NACOS_URL}/nacos/v1/cs/configs" \
    -d "dataId=${dataId}" \
    -d "group=${group}" \
    -d "content=${content}" \
    -d "type=yaml" \
    > /dev/null
  echo "    ${dataId} (group=${group}) OK"
}

# gateway
upload_config "gateway.yaml" "$(cat <<'YAML'
jwt:
  secret: ${JWT_SECRET:super-secret-key-change-in-production}
auth:
  service:
    url: ${AUTH_SERVICE_URL:http://localhost:8084}
YAML
)"

# auth-service
upload_config "auth-service.yaml" "$(cat <<'YAML'
jwt:
  secret: ${JWT_SECRET:super-secret-key-change-in-production}
  access-token-ttl: 900
  refresh-token-ttl: 604800
sms:
  provider: mock
  code-length: 6
  code-ttl: 300
wechat:
  app-id: ""
  app-secret: ""
taobao:
  app-key: ""
  app-secret: ""
YAML
)"

# leaf-service
upload_config "leaf-service.yaml" "$(cat <<'YAML'
leaf:
  snowflake:
    worker-id: ${LEAF_WORKER_ID:1}
    datacenter-id: ${LEAF_DATACENTER_ID:1}
YAML
)"

# upload-service
upload_config "upload-service.yaml" "$(cat <<'YAML'
minio:
  bucket: uploads
  presigned-expiry: 300
YAML
)"

# note-service
upload_config "note-service.yaml" "$(cat <<'YAML'
spring:
  cassandra:
    keyspace-name: notes
    contact-points: ${CASSANDRA_HOST:localhost}
    port: ${CASSANDRA_PORT:19042}
    local-datacenter: datacenter1
    schema-action: NONE
    request:
      timeout: 5s
minio:
  bucket: notes
YAML
)"

# search-service
upload_config "search-service.yaml" "$(cat <<'YAML'
# No extra config beyond shared-infra.yaml for now
YAML
)"

# search-sync-service
upload_config "search-sync-service.yaml" "$(cat <<'YAML'
spring:
  kafka:
    consumer:
      group-id: search-sync-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      max-poll-records: 500
YAML
)"

echo ""
echo "=== Nacos Config initialization complete ==="
echo ""
echo "  View configs at: ${NACOS_URL}/nacos/ (login: nacos/nacos)"
echo "  Or via API: curl ${NACOS_URL}/nacos/v1/cs/configs?dataId=shared-infra.yaml&group=COMMON"
