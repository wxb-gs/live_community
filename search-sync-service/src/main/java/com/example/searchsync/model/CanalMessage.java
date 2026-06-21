package com.example.searchsync.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class CanalMessage {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String table;
    private String type;
    private List<String> pkNames;
    private List<JsonNode> data;
    private List<JsonNode> old;

    public static CanalMessage fromJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            CanalMessage msg = new CanalMessage();
            msg.table = root.has("table") ? root.get("table").asText() : "";
            msg.type = root.has("type") ? root.get("type").asText() : "";
            msg.pkNames = new ArrayList<>();
            if (root.has("pkNames")) {
                for (JsonNode n : root.get("pkNames")) {
                    msg.pkNames.add(n.asText());
                }
            }
            msg.data = new ArrayList<>();
            if (root.has("data")) {
                for (JsonNode n : root.get("data")) {
                    msg.data.add(n);
                }
            }
            msg.old = new ArrayList<>();
            if (root.has("old")) {
                for (JsonNode n : root.get("old")) {
                    msg.old.add(n);
                }
            }
            return msg;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CanalMessage", e);
        }
    }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<String> getPkNames() { return pkNames; }
    public void setPkNames(List<String> pkNames) { this.pkNames = pkNames; }
    public List<JsonNode> getData() { return data; }
    public void setData(List<JsonNode> data) { this.data = data; }
    public List<JsonNode> getOld() { return old; }
    public void setOld(List<JsonNode> old) { this.old = old; }
}
