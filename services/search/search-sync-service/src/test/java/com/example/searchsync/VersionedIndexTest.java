package com.example.searchsync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.util.ObjectBuilder;
import com.example.common.SearchDocumentEvent;
import com.example.searchsync.infrastructure.elasticsearch.EsIndexService;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class VersionedIndexTest {
  @Test
  void tombstoneKeepsExternalVersionAndRemovesPrivateFields() throws Exception {
    var client = mock(ElasticsearchClient.class);
    List<IndexRequest<Object>> requests = new ArrayList<>();
    doAnswer(
            call -> {
              Function<IndexRequest.Builder<Object>, ObjectBuilder<IndexRequest<Object>>> f =
                  call.getArgument(0);
              requests.add(f.apply(new IndexRequest.Builder<>()).build());
              return null;
            })
        .when(client)
        .index(any(Function.class));
    var service = new EsIndexService(client);
    service.apply(
        new SearchDocumentEvent(
            1, "note", 10, 5, true, Map.of("content", "private draft", "status", "DRAFT")));
    var req = requests.get(0);
    assertEquals(VersionType.External, req.versionType());
    assertEquals(5L, req.version());
    assertEquals("notes", req.index());
    assertEquals(Map.of("id", 10L, "deleted", true, "status", "DELETED"), req.document());
  }

  @Test
  void publishedProjectionUsesEntityVersion() throws Exception {
    var client = mock(ElasticsearchClient.class);
    List<IndexRequest<Object>> requests = new ArrayList<>();
    doAnswer(
            call -> {
              Function<IndexRequest.Builder<Object>, ObjectBuilder<IndexRequest<Object>>> f =
                  call.getArgument(0);
              requests.add(f.apply(new IndexRequest.Builder<>()).build());
              return null;
            })
        .when(client)
        .index(any(Function.class));
    new EsIndexService(client)
        .apply(
            new SearchDocumentEvent(
                1, "user", 7, 9, false, Map.of("username", "alice", "status", "ACTIVE")));
    assertEquals("users", requests.get(0).index());
    assertEquals(9L, requests.get(0).version());
    assertEquals(VersionType.External, requests.get(0).versionType());
  }

  @Test
  void unknownSchemaIsNotAcknowledgedAsSuccess() {
    var client = mock(ElasticsearchClient.class);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EsIndexService(client)
                .apply(new SearchDocumentEvent(2, "note", 10, 1, false, Map.of())));
    verifyNoInteractions(client);
  }
}
