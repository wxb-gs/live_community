package com.example.upload.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.common.*;
import io.minio.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class FileServiceTest {
  JdbcTemplate jdbc = mock(JdbcTemplate.class);
  MinioClient minio = mock(MinioClient.class);
  PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
  FileService files = new FileService(jdbc, minio, tm, "uploads", "notes");

  Map<String, Object> row(String state) {
    return Map.of(
        "file_id",
        "file",
        "owner_id",
        7L,
        "note_id",
        10L,
        "object_key",
        "ready/file",
        "state",
        state,
        "expires_at",
        System.currentTimeMillis() + 60000,
        "declared_size",
        128L,
        "content_type",
        "image/png");
  }

  @Test
  void cannotPublishPendingOrAnotherUsersFile() {
    when(jdbc.queryForList(anyString(), (Object) eq("file"))).thenReturn(List.of(row("PENDING")));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> files.confirmed("file", 7L, 10L));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> files.confirmed("file", 8L, 10L));
  }

  @Test
  void fileCannotBeReboundToAnotherNote() {
    when(jdbc.queryForList(anyString(), (Object) eq("file"))).thenReturn(List.of(row("READY")));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> files.confirmed("file", 7L, 11L));
    assertEquals("uploads:ready/file", files.confirmed("file", 7L, 10L).objectKey());
  }

  @Test
  void alreadyConfirmedFileDoesNotCopyAgain() {
    when(jdbc.queryForList(anyString(), (Object) eq("file"))).thenReturn(List.of(row("READY")));
    assertEquals("file", files.confirm("file", 7L).fileId());
    verifyNoInteractions(minio);
  }

  @Test
  void expiredUploadCannotBecomeReady() {
    var data = new HashMap<>(row("PENDING"));
    data.put("expires_at", 1L);
    when(jdbc.queryForList(anyString(), (Object) eq("file"))).thenReturn(List.of(data));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> files.confirm("file", 7L));
    verifyNoInteractions(minio);
  }

  @Test
  void validatesRealImageMagicNotOnlyDeclaredType() {
    assertFalse(FileService.validImage("image/png", "not an image at all".getBytes()));
    assertTrue(
        FileService.validImage(
            "image/png", new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0}));
  }

  @Test
  void legacyAndNewReferencesAreResolvedInOneBatch() throws Exception {
    when(minio.getPresignedObjectUrl(any())).thenReturn("signed");
    var result = files.resolve(List.of("old/path.jpg", "uploads:ready/file", "uploads:ready/file"));
    assertEquals(2, result.size());
    verify(minio, times(2)).getPresignedObjectUrl(any());
  }
}
