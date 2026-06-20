package com.example.note.controller;

import com.example.common.*;
import com.example.note.service.NoteService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/note")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping("/draft")
    public Result<CreateDraftResponse> createDraft(@RequestBody CreateDraftRequest request) {
        CreateDraftResponse resp = noteService.createDraft(request);
        return Result.ok(resp);
    }

    @PostMapping("/publish")
    public Result<NoteDetailResponse> publishNote(@RequestBody PublishNoteRequest request) {
        NoteDetailResponse resp = noteService.publishNote(request);
        return Result.ok(resp);
    }

    @GetMapping("/detail")
    public Result<NoteDetailResponse> getNoteDetail(@RequestParam("noteId") Long noteId) {
        NoteDetailResponse resp = noteService.getNoteDetail(noteId);
        return Result.ok(resp);
    }

    @PostMapping("/comment")
    public Result<CommentResponse> addComment(@RequestBody CommentRequest request) {
        CommentResponse resp = noteService.addComment(request);
        return Result.ok(resp);
    }
}
