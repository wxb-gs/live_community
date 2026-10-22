package com.example.note.content.application;

import com.example.common.SearchDocumentEvent;
import com.example.note.content.domain.NoteRow;
import com.example.outbox.Outbox;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NoteSyncProducer {
  private final Outbox outbox;

  public NoteSyncProducer(Outbox outbox) {
    this.outbox = outbox;
  }

  public void sendInsertOrUpdate(NoteRow row) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("id", row.getId());
    d.put("user_id", row.getUserId());
    d.put("title", row.getTitle());
    d.put("content", row.getContent());
    d.put("summary", row.getSummary());
    d.put("tags", row.getTags());
    d.put("category", row.getCategory());
    d.put("view_count", row.getViewCount());
    d.put("like_count", row.getLikeCount());
    d.put("status", row.getStatus());
    d.put("created_at", row.getCreatedAt());
    outbox.append(
        "search_documents",
        "note:" + row.getId(),
        new SearchDocumentEvent(
            1, "note", row.getId(), row.getVersion(), !"PUBLISHED".equals(row.getStatus()), d));
  }
}
