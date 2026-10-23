package com.example.common;

import java.util.List;

public interface NoteRpcService {

  CreateDraftResponse createDraft(CreateDraftRequest request);

  NoteDetailResponse publishNote(PublishNoteRequest request);

  NoteDetailResponse getNoteDetail(Long noteId);

  CommentResponse addComment(CommentRequest request);

  /** 获取根评论列表（分页） */
  List<CommentResponse> listRootComments(Long noteId, int page, int size);

  /** 获取某条根评论下的子回复列表（分页） */
  List<CommentResponse> listReplies(Long rootId, int page, int size);

  List<NoteDetailResponse> listPublishedNotes(int page, int size);

  List<NoteDetailResponse> listUserNotes(long userId, int page, int size);
}
