package com.example.common;

import java.util.List;

public interface NoteRpcService {

    CreateDraftResponse createDraft(CreateDraftRequest request);

    NoteDetailResponse publishNote(PublishNoteRequest request);

    NoteDetailResponse getNoteDetail(Long noteId);

    CommentResponse addComment(CommentRequest request);

    List<NoteDetailResponse> listPublishedNotes(int page, int size);
}
