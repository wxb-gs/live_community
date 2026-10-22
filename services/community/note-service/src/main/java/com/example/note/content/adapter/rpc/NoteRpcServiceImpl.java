package com.example.note.content.adapter.rpc;

import com.example.common.*;
import com.example.note.content.application.NoteService;
import java.util.List;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class NoteRpcServiceImpl implements NoteRpcService {

  private final NoteService noteService;

  public NoteRpcServiceImpl(NoteService noteService) {
    this.noteService = noteService;
  }

  @Override
  public CreateDraftResponse createDraft(CreateDraftRequest request) {
    return noteService.createDraft(request);
  }

  @Override
  public NoteDetailResponse publishNote(PublishNoteRequest request) {
    return noteService.publishNote(request);
  }

  @Override
  public NoteDetailResponse getNoteDetail(Long noteId) {
    return noteService.getNoteDetail(noteId);
  }

  @Override
  public CommentResponse addComment(CommentRequest request) {
    return noteService.addComment(request);
  }

  @Override
  public List<CommentResponse> listRootComments(Long noteId, int page, int size) {
    return noteService.listRootComments(noteId, page, size);
  }

  @Override
  public List<CommentResponse> listReplies(Long rootId, int page, int size) {
    return noteService.listReplies(rootId, page, size);
  }

  @Override
  public List<NoteDetailResponse> listPublishedNotes(int page, int size) {
    return noteService.listPublishedNotes(page, size);
  }

  @Override
  public List<NoteDetailResponse> listUserNotes(long userId, int page, int size) {
    return noteService.listUserNotes(userId, page, size);
  }
}
