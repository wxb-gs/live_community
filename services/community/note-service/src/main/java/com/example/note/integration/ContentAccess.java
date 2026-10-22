package com.example.note.integration;

import com.example.note.access.TargetAccess;
import com.example.note.comment.infrastructure.CommentIndexRepository;
import com.example.note.content.infrastructure.NoteMysqlRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentAccess implements TargetAccess {
  private final NoteMysqlRepository notes;
  private final CommentIndexRepository comments;

  public ContentAccess(NoteMysqlRepository notes, CommentIndexRepository comments) {
    this.notes = notes;
    this.comments = comments;
  }

  public void requirePublic(String type, Long id) {
    if (id == null || id <= 0) throw new IllegalArgumentException("Invalid target id");
    if ("comment".equals(type)) {
      var comment = comments.findById(id).orElseThrow(() -> missing());
      if (comment.getState() != 0) throw missing();
      id = comment.getObjId();
    } else if (!"note".equals(type)) throw new IllegalArgumentException("Invalid target type");
    var row = notes.findById(id).orElseThrow(() -> missing());
    if (!"PUBLISHED".equals(row.getStatus())) throw missing();
  }

  private ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Target not public");
  }
}
