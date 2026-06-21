#!/bin/bash
ES_URL="http://${ES_HOST:-localhost}:9200"

# Wait for ES to be ready
until curl -s "$ES_URL/_cluster/health" > /dev/null; do
  echo "Waiting for ES..."
  sleep 2
done

# Create notes index with IK
curl -s -X PUT "$ES_URL/notes" -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_max_word": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart": { "type": "custom", "tokenizer": "ik_smart" }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "user_id": { "type": "long" },
      "title": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "content": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "summary": { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
      "tags": { "type": "text" },
      "category": {
        "type": "text",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "status": {
        "type": "text",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "cover_url": { "type": "keyword" },
      "view_count": { "type": "long" },
      "like_count": { "type": "long" },
      "favorite_count": { "type": "long" },
      "created_at": { "type": "date" }
    }
  }
}'

# Create users index with IK
curl -s -X PUT "$ES_URL/users" -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_max_word": { "type": "custom", "tokenizer": "ik_max_word" },
        "ik_smart": { "type": "custom", "tokenizer": "ik_smart" }
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
      "nickname": {
        "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"
      },
      "avatar": { "type": "keyword" },
      "status": { "type": "keyword" }
    }
  }
}'

echo "ES indices created successfully"
