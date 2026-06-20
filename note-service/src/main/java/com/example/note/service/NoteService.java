package com.example.note.service;

import com.example.common.*;
import com.example.note.entity.CommentEntity;
import com.example.note.entity.NoteEntity;
import com.example.note.repository.CommentRepository;
import com.example.note.repository.NoteRepository;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final NoteRepository noteRepository;
    private final CommentRepository commentRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @DubboReference(check = false)
    private LeafRpcService leafRpcService;

    public NoteService(NoteRepository noteRepository, CommentRepository commentRepository, MinioClient minioClient) {
        this.noteRepository = noteRepository;
        this.commentRepository = commentRepository;
        this.minioClient = minioClient;
    }

    public CreateDraftResponse createDraft(CreateDraftRequest request) {
        IdResponse idResp = leafRpcService.generateSegmentId("note");
        long noteId = idResp.getId();
        long now = System.currentTimeMillis();

        NoteEntity entity = new NoteEntity();
        entity.setId(noteId);
        entity.setUserId(request.getUserId());
        entity.setTitle(request.getTitle());
        entity.setContent(request.getContent());
        entity.setSummary(extractSummary(request.getContent()));
        entity.setStatus("DRAFT");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        noteRepository.save(entity);
        log.info("Draft created: noteId={}, userId={}", noteId, request.getUserId());
        return new CreateDraftResponse(noteId, "DRAFT");
    }

    public NoteDetailResponse publishNote(PublishNoteRequest request) {
        NoteEntity entity = noteRepository.findById(request.getNoteId())
                .orElseThrow(() -> new RuntimeException("Note not found: " + request.getNoteId()));

        if (!"DRAFT".equals(entity.getStatus())) {
            throw new RuntimeException("Note is not in DRAFT status: " + entity.getStatus());
        }

        String objectKey = generateObjectKey(entity.getId(), request.getFileName());
        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(10, TimeUnit.MINUTES)
                            .build()
            );

            entity.setObjectKey(objectKey);
            entity.setStatus("PUBLISHED");
            entity.setUpdatedAt(System.currentTimeMillis());
            noteRepository.save(entity);

            log.info("Note published: noteId={}, objectKey={}", entity.getId(), objectKey);

            NoteDetailResponse resp = toDetailResponse(entity);
            resp.setUploadUrl(uploadUrl);
            resp.setComments(Collections.emptyList());
            return resp;
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for noteId={}", entity.getId(), e);
            throw new RuntimeException("Failed to publish note", e);
        }
    }

    public NoteDetailResponse getNoteDetail(Long noteId) {
        CompletableFuture<NoteEntity> noteFuture = CompletableFuture.supplyAsync(() ->
                noteRepository.findById(noteId)
                        .orElseThrow(() -> new RuntimeException("Note not found: " + noteId))
        );

        CompletableFuture<List<CommentEntity>> commentsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return commentRepository.findByNoteId(noteId);
            } catch (Exception e) {
                log.warn("Failed to load comments for noteId={}", noteId, e);
                return Collections.emptyList();
            }
        });

        NoteEntity note = noteFuture.join();
        List<CommentEntity> commentEntities = commentsFuture.join();

        NoteDetailResponse resp = toDetailResponse(note);
        if (note.getObjectKey() != null && !note.getObjectKey().isEmpty()) {
            try {
                String coverUrl = minioClient.getPresignedObjectUrl(
                        io.minio.GetPresignedObjectUrlArgs.builder()
                                .method(io.minio.http.Method.GET)
                                .bucket(bucket)
                                .object(note.getObjectKey())
                                .expiry(24, TimeUnit.HOURS)
                                .build());
                resp.setUploadUrl(coverUrl);
            } catch (Exception e) {
                log.warn("Failed to generate cover URL for noteId={}", noteId, e);
            }
        }
        resp.setComments(commentEntities.stream()
                .map(this::toCommentResponse)
                .collect(Collectors.toList()));
        return resp;
    }

    public CommentResponse addComment(CommentRequest request) {
        IdResponse idResp = leafRpcService.generateSegmentId("comment");
        long commentId = idResp.getId();
        long now = System.currentTimeMillis();

        CommentEntity entity = new CommentEntity();
        entity.setNoteId(request.getNoteId());
        entity.setCommentId(commentId);
        entity.setUserId(request.getUserId());
        entity.setContent(request.getContent());
        entity.setCreatedAt(now);

        commentRepository.save(entity);
        log.info("Comment added: noteId={}, commentId={}", request.getNoteId(), commentId);
        return new CommentResponse(commentId, request.getNoteId(), request.getUserId(),
                request.getContent(), now);
    }

    private String generateObjectKey(Long noteId, String fileName) {
        String datePath = java.time.LocalDate.now().toString().replace("-", "/");
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s/%d_%s_%s", datePath, noteId, uuid, fileName);
    }

    private String extractSummary(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    private NoteDetailResponse toDetailResponse(NoteEntity entity) {
        NoteDetailResponse resp = new NoteDetailResponse();
        resp.setNoteId(entity.getId());
        resp.setUserId(entity.getUserId());
        resp.setTitle(entity.getTitle());
        resp.setContent(entity.getContent());
        resp.setSummary(entity.getSummary());
        resp.setObjectKey(entity.getObjectKey());
        resp.setStatus(entity.getStatus());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }

    private CommentResponse toCommentResponse(CommentEntity entity) {
        return new CommentResponse(entity.getCommentId(), entity.getNoteId(),
                entity.getUserId(), entity.getContent(), entity.getCreatedAt());
    }

    public List<NoteDetailResponse> listPublishedNotes(int page, int size) {
        List<NoteEntity> entities = noteRepository.findPublished(size);
        return entities.stream()
                .map(entity -> {
                    NoteDetailResponse resp = toDetailResponse(entity);
                    if (entity.getObjectKey() != null && !entity.getObjectKey().isEmpty()) {
                        try {
                            String coverUrl = minioClient.getPresignedObjectUrl(
                                    io.minio.GetPresignedObjectUrlArgs.builder()
                                            .method(io.minio.http.Method.GET)
                                            .bucket(bucket)
                                            .object(entity.getObjectKey())
                                            .expiry(24, TimeUnit.HOURS)
                                            .build());
                            resp.setUploadUrl(coverUrl);
                        } catch (Exception e) {
                            log.warn("Failed to generate cover URL for noteId={}", entity.getId(), e);
                        }
                    }
                    return resp;
                })
                .collect(Collectors.toList());
    }
}
