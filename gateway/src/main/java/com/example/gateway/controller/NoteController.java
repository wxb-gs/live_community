package com.example.gateway.controller;

import com.example.common.*;
import java.util.List;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/note")
public class NoteController {

    @DubboReference(check = false)
    private NoteRpcService noteRpcService;

    @PostMapping("/draft")
    public Result<CreateDraftResponse> createDraft(@RequestBody CreateDraftRequest request) {
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
        return Result.ok(resp);
    }

    @PostMapping("/comment")
    public Result<CommentResponse> addComment(@RequestBody CommentRequest request) {
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
