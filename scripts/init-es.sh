#!/bin/bash
# ============================================================
# Initialize Elasticsearch indices with IK mappings
# Must be run AFTER ES is healthy and AFTER IK plugin installed
# ============================================================
set -e

ES_URL="${ES_URL:-http://localhost:9200}"

echo "=== Creating ES indices ==="

# ---- notes index ----
echo "Creating 'notes' index..."
curl -sf -X PUT "$ES_URL/notes" -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_max_word": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart":   { "type": "custom", "tokenizer": "ik_smart" }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":         { "type": "long" },
      "user_id":    { "type": "long" },
      "title": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "suggest": { "type": "completion" } }
      },
      "content":    { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "summary":    { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "tags":       { "type": "keyword" },
      "category":   { "type": "keyword" },
      "view_count": { "type": "integer" },
      "like_count": { "type": "integer" },
      "status":     { "type": "keyword" },
      "created_at": { "type": "date" }
    }
  }
}' | tee /dev/null
echo ""

# ---- users index ----
echo "Creating 'users' index..."
curl -sf -X PUT "$ES_URL/users" -H 'Content-Type: application/json' -d '{
  "settings": {
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
        "fields": { "keyword": { "type": "keyword" }, "suggest": { "type": "completion" } }
      },
      "nickname": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "suggest": { "type": "completion" } }
      },
      "avatar":  { "type": "keyword" },
      "status":  { "type": "keyword" }
    }
  }
}' | tee /dev/null
echo ""

echo "=== Indices created ==="
curl -sf "$ES_URL/_cat/indices?v"
