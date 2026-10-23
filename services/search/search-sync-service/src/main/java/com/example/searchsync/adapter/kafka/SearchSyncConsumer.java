package com.example.searchsync.adapter.kafka;

import com.example.common.SearchDocumentEvent;
import com.example.searchsync.infrastructure.elasticsearch.EsIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SearchSyncConsumer {
  private final EsIndexService index;
  private final ObjectMapper mapper;

  public SearchSyncConsumer(EsIndexService index, ObjectMapper mapper) {
    this.index = index;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "search_documents", groupId = "search-documents-v1")
  public void onMessage(String raw) throws Exception {
    index.apply(mapper.readValue(raw, SearchDocumentEvent.class));
  }
}
