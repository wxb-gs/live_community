#!/bin/bash
# ============================================================
# Initialize Elasticsearch indices with IK analyzer
# Requires: IK plugin installed on ES
# ============================================================
set -e

ES_URL="${ES_URL:-http://localhost:9200}"

echo "=== Creating ES indices ==="

# ---- notes index ----
echo "Creating 'notes' index..."
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
}' | tee /dev/null
echo ""

# ---- users index ----
echo "Creating 'users' index..."
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
}' | tee /dev/null
echo ""

echo "=== Indices created ==="
curl -sf "$ES_URL/_cat/indices?v"
