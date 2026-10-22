package com.example.note.content.application;

import com.example.common.*;
import com.example.note.comment.application.CommentService;
import com.example.note.content.domain.NoteRow;
import com.example.note.content.infrastructure.NoteMysqlRepository;
import com.example.note.interaction.application.InteractionService;
import java.util.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NoteService {
  private final NoteMysqlRepository repository;
  private final InteractionService interactions;
  private final CommentService comments;

  @org.springframework.beans.factory.annotation.Autowired
  private com.example.leafclient.LeafIdClient leaf;

  @DubboReference(check = false, timeout = 3000, retries = 0)
  private UploadRpcService files;

  public NoteService(
      NoteMysqlRepository repository, InteractionService interactions, CommentService comments) {
    this.repository = repository;
    this.interactions = interactions;
    this.comments = comments;
  }

  public CreateDraftResponse createDraft(CreateDraftRequest request) {
    if (request.getUserId() == null || request.getUserId() <= 0)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity required");
    if (request.getTitle() == null
        || request.getTitle().isBlank()
        || request.getTitle().length() > 100
        || request.getContent() == null
        || request.getContent().length() > 5000)
      throw new IllegalArgumentException("Title and content required within limits");
    long id = leaf.generateSegmentId("note").getId(), now = System.currentTimeMillis();
    String text = request.getContent();
    repository.upsert(
        id,
        request.getUserId(),
        request.getTitle(),
        text,
        text.substring(0, Math.min(200, text.length())),
        null,
        null,
        null,
        "DRAFT",
        now,
        now);
    return new CreateDraftResponse(id, "DRAFT");
  }

  public NoteDetailResponse publishNote(PublishNoteRequest request) {
    if (request.getUserId() == null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity required");
    if (request.getNoteId() == null || request.getFileId() == null)
      throw new IllegalArgumentException("noteId and confirmed fileId required");
    var old =
        repository
            .findById(request.getNoteId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    if (!old.getUserId().equals(request.getUserId()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Author required");
    // File is permanently scoped to its owner and note. Confirmed object is immutable; no
    // cross-service transaction needed.
    var file = files.confirmedFile(request.getFileId(), request.getUserId(), request.getNoteId());
    return enrich(
            List.of(
                repository.publish(
                    old.getId(), request.getUserId(), file.fileId(), file.objectKey())))
        .get(0);
  }

  public NoteDetailResponse getNoteDetail(Long id) {
    return getNoteDetailForUser(id, null);
  }

  public NoteDetailResponse getNoteDetailForUser(Long id, Long userId) {
    var row =
        repository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found"));
    if (!"PUBLISHED".equals(row.getStatus())
        && !("DRAFT".equals(row.getStatus()) && row.getUserId().equals(userId)))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Note not public");
    var response = enrich(List.of(row), userId == null ? 0L : userId).get(0);
    if ("PUBLISHED".equals(row.getStatus()))
      response.setComments(comments.listRootComments(id, 0, 20));
    else response.setComments(List.of());
    return response;
  }

  public void recordView(Long id, Long userId, String eventId) {
    repository.recordView(id, userId, eventId);
  }

  public CommentResponse addComment(CommentRequest request) {
    return comments.addComment(request);
  }

  public List<CommentResponse> listRootComments(Long id, int page, int size) {
    return comments.listRootComments(id, page, size);
  }

  public List<CommentResponse> listReplies(Long id, int page, int size) {
    return comments.listReplies(id, page, size);
  }

  public List<NoteDetailResponse> listUserNotes(long userId, int page, int size) {
    page(page, size);
    return enrich(repository.findByUserId(userId, Math.multiplyExact(page, size), size), userId);
  }

  public List<NoteDetailResponse> listPublishedNotes(int page, int size) {
    page(page, size);
    return enrich(repository.findPublished(Math.multiplyExact(page, size), size));
  }

  public static void page(int page, int size) {
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw new IllegalArgumentException("Invalid pagination");
  }

  public List<NoteDetailResponse> listPublishedNotesForUser(int page, int size, Long userId) {
    page(page, size);
    return enrich(repository.findPublished(Math.multiplyExact(page, size), size), userId);
  }

  private List<NoteDetailResponse> enrich(List<NoteRow> rows) {
    return enrich(rows, 0L);
  }

  private List<NoteDetailResponse> enrich(List<NoteRow> rows, Long userId) {
    var keys =
        rows.stream()
            .map(NoteRow::getObjectKey)
            .filter(Objects::nonNull)
            .filter(k -> !k.isBlank())
            .distinct()
            .toList();
    Map<String, String> urls = keys.isEmpty() ? Map.of() : files.resolveFiles(keys);
    var ids = rows.stream().map(NoteRow::getId).toList();
    var likes = interactions.batchStatus(InteractionType.LIKE, "note", ids, userId);
    var favorites = interactions.batchStatus(InteractionType.FAVORITE, "note", ids, userId);
    return rows.stream()
        .map(
            row -> {
              var d = new NoteDetailResponse();
              d.setNoteId(row.getId());
              d.setUserId(row.getUserId());
              d.setTitle(row.getTitle());
              d.setContent(row.getContent());
              d.setSummary(row.getSummary());
              d.setObjectKey(row.getObjectKey());
              d.setStatus(row.getStatus());
              d.setCreatedAt(row.getCreatedAt());
              d.setUpdatedAt(row.getUpdatedAt());
              d.setCoverUrl(urls.get(row.getObjectKey()));
              d.setLiked(likes.get(row.getId()).active());
              d.setFavorited(favorites.get(row.getId()).active());
              d.setLikeCount(likes.get(row.getId()).count());
              d.setFavoriteCount(favorites.get(row.getId()).count());
              return d;
            })
        .toList();
  }
}
