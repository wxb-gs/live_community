package com.example.note;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.common.*;
import com.example.note.comment.application.CommentService;
import com.example.note.content.application.NoteService;
import com.example.note.content.domain.NoteRow;
import com.example.note.content.infrastructure.NoteMysqlRepository;
import com.example.note.interaction.application.InteractionService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class PublicationTest {
  NoteMysqlRepository repository = mock(NoteMysqlRepository.class);
  InteractionService interactions = mock(InteractionService.class);
  CommentService comments = mock(CommentService.class);
  UploadRpcService files = mock(UploadRpcService.class);
  NoteService service = new NoteService(repository, interactions, comments);

  @BeforeEach
  void init() {
    ReflectionTestUtils.setField(service, "files", files);
  }

  NoteRow row() {
    var r = new NoteRow();
    r.setId(10L);
    r.setUserId(7L);
    r.setStatus("DRAFT");
    return r;
  }

  @Test
  void otherUserCannotPublishBeforeAnyFileCall() {
    when(repository.findById(10)).thenReturn(Optional.of(row()));
    var req = new PublishNoteRequest();
    req.setNoteId(10L);
    req.setUserId(8L);
    req.setFileId("file");
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.publishNote(req));
    verifyNoInteractions(files);
    verify(repository, never()).publish(anyLong(), anyLong(), anyString(), anyString());
  }

  @Test
  void unconfirmedFileCannotChangeNoteState() {
    when(repository.findById(10)).thenReturn(Optional.of(row()));
    when(files.confirmedFile("file", 7L, 10L)).thenThrow(new IllegalArgumentException("Not READY"));
    var req = new PublishNoteRequest();
    req.setNoteId(10L);
    req.setUserId(7L);
    req.setFileId("file");
    assertThrows(IllegalArgumentException.class, () -> service.publishNote(req));
    verify(repository, never()).publish(anyLong(), anyLong(), anyString(), anyString());
  }

  @Test
  void privateDraftIsInvisibleToOthers() {
    when(repository.findById(10)).thenReturn(Optional.of(row()));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.getNoteDetailForUser(10L, 8L));
  }
}
