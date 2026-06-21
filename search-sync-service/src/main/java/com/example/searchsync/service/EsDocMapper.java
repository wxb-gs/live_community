package com.example.searchsync.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public class EsDocMapper {

    public static Map<String, Object> noteFromMySql(JsonNode row) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", asLong(row, "id"));
        doc.put("user_id", asLong(row, "user_id"));
        doc.put("title", asString(row, "title"));
        doc.put("content", asString(row, "content"));
        doc.put("summary", asString(row, "summary"));
        doc.put("tags", asString(row, "tags"));
        doc.put("category", asString(row, "category"));
        doc.put("view_count", asInt(row, "view_count"));
        doc.put("like_count", asInt(row, "like_count"));
        doc.put("status", asString(row, "status"));
        doc.put("created_at", asLong(row, "created_at"));
        return doc;
    }

    public static Map<String, Object> userFromMySql(JsonNode row) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", asLong(row, "user_id"));
        doc.put("username", asString(row, "username"));
        doc.put("nickname", asString(row, "nickname"));
        doc.put("avatar", asString(row, "avatar"));
        doc.put("status", asString(row, "status"));
        return doc;
    }

    private static String asString(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node != null && !node.isNull() ? node.asText() : "";
    }

    private static long asLong(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node != null && !node.isNull() ? node.asLong() : 0L;
    }

    private static int asInt(JsonNode row, String field) {
        JsonNode node = row.get(field);
        return node != null && !node.isNull() ? node.asInt() : 0;
    }
}
