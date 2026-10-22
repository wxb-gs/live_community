package com.example.searchsync.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import com.example.common.SearchDocumentEvent;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EsIndexService {
  private final ElasticsearchClient client;

  public EsIndexService(ElasticsearchClient client) {
    this.client = client;
  }

  public void apply(SearchDocumentEvent e) throws Exception {
    if (e.schemaVersion() != 1
        || e.entityId() <= 0
        || e.version() <= 0
        || !Set.of("note", "user").contains(e.entityType())
        || e.document() == null) throw new IllegalArgumentException("Invalid search event");
    String index = "note".equals(e.entityType()) ? "notes" : "users";
    Map<String, Object> doc = new LinkedHashMap<>(e.document());
    doc.put("id", e.entityId());
    doc.put("deleted", e.deleted());
    if (e.deleted()) {
      doc.clear();
      doc.put("id", e.entityId());
      doc.put("deleted", true);
      doc.put("status", "DELETED");
    }
    // Index tombstones permanently, so delayed updates cannot resurrect deleted content.
    try {
      client.index(
          i ->
              i.index(index)
                  .id(Long.toString(e.entityId()))
                  .version(e.version())
                  .versionType(VersionType.External)
                  .document(doc));
    } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException ex) {
      if (ex.status() != 409) throw ex;
    }
  }
}
