package com.example.note.content.adapter.http;

import com.example.common.*;
import com.example.note.content.application.NoteService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/note")
public class NoteController {
  private final NoteService notes;

  public NoteController(NoteService notes) {
    this.notes = notes;
  }

  @PostMapping("/draft")
  public Result<CreateDraftResponse> draft(
      @AuthenticationPrincipal Long userId, @RequestBody CreateDraftRequest req) {
    req.setUserId(userId);
    return Result.ok(notes.createDraft(req));
  }

  @PostMapping("/publish")
  public Result<NoteDetailResponse> publish(
      @AuthenticationPrincipal Long userId, @RequestBody PublishNoteRequest req) {
    req.setUserId(userId);
    return Result.ok(notes.publishNote(req));
  }

  @GetMapping("/detail")
  public Result<NoteDetailResponse> detail(
      @AuthenticationPrincipal Long userId,
      @RequestParam Long noteId,
      @RequestHeader(value = "X-View-Id", required = false) String eventId) {
    var detail = notes.getNoteDetailForUser(noteId, userId);
    if ("PUBLISHED".equals(detail.getStatus()))
      notes.recordView(
          noteId, userId, eventId == null ? java.util.UUID.randomUUID().toString() : eventId);
    return Result.ok(detail);
  }

  @PostMapping("/comment")
  public Result<CommentResponse> comment(
      @AuthenticationPrincipal Long userId, @RequestBody CommentRequest req) {
    req.setUserId(userId);
    return Result.ok(notes.addComment(req));
  }

  @GetMapping("/comments")
  public Result<List<CommentResponse>> comments(
      @RequestParam Long noteId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.ok(notes.listRootComments(noteId, page, size));
  }

  @GetMapping("/replies")
  public Result<List<CommentResponse>> replies(
      @RequestParam Long rootId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.ok(notes.listReplies(rootId, page, size));
  }

  @GetMapping("/list")
  public Result<List<NoteDetailResponse>> list(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.ok(notes.listPublishedNotesForUser(page, size, userId));
  }

  @GetMapping("/my")
  public Result<List<NoteDetailResponse>> my(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return Result.ok(notes.listUserNotes(userId, page, size));
  }
}
