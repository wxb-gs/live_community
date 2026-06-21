package com.example.gateway.controller;

import com.example.common.*;
import java.util.List;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/note")
public class NoteController {

    @DubboReference(check = false)
    private NoteRpcService noteRpcService;

    private final StringRedisTemplate redisTemplate;

    public NoteController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/draft")
    public Result<CreateDraftResponse> createDraft(@AuthenticationPrincipal Long userId,
                                                    @RequestBody CreateDraftRequest request) {
        request.setUserId(userId);
        CreateDraftResponse resp = noteRpcService.createDraft(request);
        return Result.ok(resp);
    }

    @PostMapping("/publish")
    public Result<NoteDetailResponse> publishNote(@RequestBody PublishNoteRequest request) {
        NoteDetailResponse resp = noteRpcService.publishNote(request);
        return Result.ok(resp);
    }

    @GetMapping("/detail")
    public Result<NoteDetailResponse> getNoteDetail(@RequestParam("noteId") Long noteId) {
        NoteDetailResponse resp = noteRpcService.getNoteDetail(noteId);
        try {
            redisTemplate.opsForValue().increment("note:view:" + noteId);
        } catch (Exception ignored) {
            // non-blocking: view count is best-effort
        }
        return Result.ok(resp);
    }

    @PostMapping("/comment")
    public Result<CommentResponse> addComment(@AuthenticationPrincipal Long userId,
                                               @RequestBody CommentRequest request) {
        request.setUserId(userId);
        CommentResponse resp = noteRpcService.addComment(request);
        return Result.ok(resp);
    }

    @GetMapping("/list")
    public Result<List<NoteDetailResponse>> listNotes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<NoteDetailResponse> list = noteRpcService.listPublishedNotes(page, size);
        return Result.ok(list);
    }
}
