package com.example.searchsync.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EsIndexService {

    private static final Logger log = LoggerFactory.getLogger(EsIndexService.class);
    private static final String NOTES_INDEX = "notes";
    private static final String USERS_INDEX = "users";

    private final ElasticsearchClient esClient;

    public EsIndexService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 索引笔记文档到 ES，使用 PUT 语义（相同 id 覆盖写入，天然幂等）。
     * @return true 表示全部成功，false 表示有文档写入失败（消息不应 ACK）
     */
    public boolean indexNote(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> doc = EsDocMapper.noteFromMySql(row);
            long id = (long) doc.get("id");
            ops.add(BulkOperation.of(b -> b
                .index(IndexOperation.of(i -> i.index(NOTES_INDEX).id(String.valueOf(id)).document(doc)))));
        }
        return executeBulk(ops, NOTES_INDEX);
    }

    public boolean deleteNote(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            String id = String.valueOf(EsDocMapper.noteFromMySql(row).get("id"));
            ops.add(BulkOperation.of(b -> b
                .delete(DeleteOperation.of(d -> d.index(NOTES_INDEX).id(id)))));
        }
        return executeBulk(ops, NOTES_INDEX);
    }

    public boolean indexUser(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, Object> doc = EsDocMapper.userFromMySql(row);
            long id = (long) doc.get("id");
            ops.add(BulkOperation.of(b -> b
                .index(IndexOperation.of(i -> i.index(USERS_INDEX).id(String.valueOf(id)).document(doc)))));
        }
        return executeBulk(ops, USERS_INDEX);
    }

    public boolean deleteUser(List<JsonNode> rows) {
        List<BulkOperation> ops = new ArrayList<>();
        for (JsonNode row : rows) {
            String id = String.valueOf(EsDocMapper.userFromMySql(row).get("id"));
            ops.add(BulkOperation.of(b -> b
                .delete(DeleteOperation.of(d -> d.index(USERS_INDEX).id(id)))));
        }
        return executeBulk(ops, USERS_INDEX);
    }

    private boolean executeBulk(List<BulkOperation> ops, String index) {
        if (ops.isEmpty()) return true;
        try {
            BulkResponse response = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
            if (response.errors()) {
                List<String> errors = response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> String.format("id=%s reason=%s", item.id(), item.error().reason()))
                    .toList();
                log.error("Bulk errors on [{}]: {}", index, String.join("; ", errors));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Bulk index failed for [{}]", index, e);
            return false;
        }
    }
}
