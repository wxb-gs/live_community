#!/bin/bash
# ============================================================
# 一键初始化全部环境
# 用法: bash scripts/init-all.sh
#
# 包含:
#   1. Nacos 配置中心初始化
#   2. Elasticsearch 索引创建 (IK 分词)
#   3. 测试数据初始化 (用户 + 笔记 + 封面图)
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

ES_URL="${ES_URL:-http://localhost:9200}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

step()  { echo -e "\n${GREEN}===${NC} $1 ${GREEN}===${NC}"; }
info()  { echo -e "  ${YELLOW}→${NC} $1"; }
ok()    { echo -e "  ${GREEN}✓${NC} $1"; }
fail()  { echo -e "  ${RED}✗${NC} $1"; }

# ──────────────────────────────────────────────────────────
# Step 1: Nacos Config
# ──────────────────────────────────────────────────────────
step "Step 1/3: Nacos 配置中心初始化"
if [ -f "$SCRIPT_DIR/init-nacos-config.sh" ]; then
  bash "$SCRIPT_DIR/init-nacos-config.sh"
else
  fail "找不到 init-nacos-config.sh"
fi

# ──────────────────────────────────────────────────────────
# Step 2: Elasticsearch Indices
# ──────────────────────────────────────────────────────────
step "Step 2/3: Elasticsearch 索引创建"

info "检查 ES 连通性..."
if ! curl -sf "$ES_URL/_cluster/health" > /dev/null 2>&1; then
  fail "无法连接 ES ($ES_URL)，请确认 elasticsearch 容器已启动"
  exit 1
fi
ok "ES 连接正常"

info "删除旧索引 (如存在)..."
curl -sf -X DELETE "$ES_URL/notes"  > /dev/null 2>&1 || true
curl -sf -X DELETE "$ES_URL/users"  > /dev/null 2>&1 || true

info "创建 notes 索引..."
curl -sf -X PUT "$ES_URL/notes" -H 'Content-Type: application/json' -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_max_word": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart":   { "type": "custom", "tokenizer": "ik_smart" }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":             { "type": "long" },
      "user_id":        { "type": "long" },
      "title": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "content":        { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "summary":        { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "tags":           { "type": "text" },
      "category": {
        "type": "text",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "status": {
        "type": "text",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "cover_url":      { "type": "keyword" },
      "view_count":     { "type": "long" },
      "like_count":     { "type": "long" },
      "favorite_count": { "type": "long" },
      "created_at":     { "type": "date" }
    }
  }
}' > /dev/null && ok "notes 索引已创建"

info "创建 users 索引..."
curl -sf -X PUT "$ES_URL/users" -H 'Content-Type: application/json' -d '{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_max_word": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart":   { "type": "custom", "tokenizer": "ik_smart" }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "username": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "nickname": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "avatar":  { "type": "keyword" },
      "status":  { "type": "keyword" }
    }
  }
}' > /dev/null && ok "users 索引已创建"

# ──────────────────────────────────────────────────────────
# Step 3: Test Data
# ──────────────────────────────────────────────────────────
step "Step 3/3: 测试数据初始化"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" = "000" ]; then
  fail "无法连接 Gateway ($GATEWAY_URL)，请确认 Java 服务已启动"
  info "跳过测试数据初始化，可稍后手动运行: python scripts/init-test-data.py"
  exit 0
fi
ok "Gateway 连接正常"

if [ -f "$SCRIPT_DIR/init-test-data.py" ]; then
  PYTHONIOENCODING=utf-8 python "$SCRIPT_DIR/init-test-data.py"
else
  fail "找不到 init-test-data.py"
  exit 1
fi

# ──────────────────────────────────────────────────────────
# Done
# ──────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}=============================================${NC}"
echo -e "${GREEN}  全部初始化完成！${NC}"
echo -e "${GREEN}=============================================${NC}"
echo ""
echo "  前端: http://localhost:5173"
echo "  搜索: http://localhost:5173/search?q=旅行"
echo "  Nacos: http://localhost:18848/nacos/ (nacos/nacos)"
echo ""
