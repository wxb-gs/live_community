package com.example.upload.service;

import com.example.common.*;
import io.minio.*;
import io.minio.http.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileService {
  private final JdbcTemplate jdbc;
  private final MinioClient minio;
  private final TransactionTemplate tx;
  private final String bucket, legacyBucket;

  public FileService(
      JdbcTemplate jdbc,
      MinioClient minio,
      PlatformTransactionManager tm,
      @Value("${minio.bucket:uploads}") String bucket,
      @Value("${minio.legacy-bucket:notes}") String legacyBucket) {
    this.jdbc = jdbc;
    this.minio = minio;
    this.tx = new TransactionTemplate(tm);
    this.bucket = bucket;
    this.legacyBucket = legacyBucket;
  }

  public PresignedUrlResponse prepare(PresignedUrlRequest req) {
    if (req.getUserId() == null
        || req.getNoteId() == null
        || req.getNoteId() <= 0
        || req.getSize() <= 0
        || req.getSize() > 10 * 1024 * 1024
        || !Set.of("image/jpeg", "image/png", "image/gif", "image/webp")
            .contains(req.getContentType() == null ? "" : req.getContentType()))
      throw new IllegalArgumentException("Image, noteId and size (1..10MiB) required");
    String id = UUID.randomUUID().toString(), key = "pending/" + req.getUserId() + "/" + id;
    long expires = System.currentTimeMillis() + 300_000;
    jdbc.update(
        "INSERT INTO"
            + " file_metadata(file_id,owner_id,note_id,object_key,content_type,declared_size,state,expires_at)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id,
        req.getUserId(),
        req.getNoteId(),
        key,
        req.getContentType(),
        req.getSize(),
        "PENDING",
        expires);
    try {
      String url =
          minio.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucket)
                  .object(key)
                  .expiry(300, TimeUnit.SECONDS)
                  .build());
      var response = new PresignedUrlResponse(url, key, expires / 1000);
      response.setFileId(id);
      return response;
    } catch (Exception e) {
      throw new IllegalStateException("Cannot prepare upload", e);
    }
  }

  public FileReference confirm(String id, Long userId) {
    return tx.execute(
        status -> {
          var row = metadata(id, true);
          checkOwner(row, userId);
          if ("READY".equals(row.get("state"))) return reference(row);
          if (((Number) row.get("expires_at")).longValue() < System.currentTimeMillis())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload expired");
          String source = row.get("object_key").toString(), target = "ready/" + id;
          try {
            var stat =
                minio.statObject(StatObjectArgs.builder().bucket(bucket).object(source).build());
            if (stat.size() != ((Number) row.get("declared_size")).longValue()
                || stat.size() > 10 * 1024 * 1024
                || !row.get("content_type").equals(stat.contentType()))
              throw new IllegalArgumentException("Uploaded size or type mismatch");
            byte[] magic;
            try (var stream =
                minio.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(source)
                        .matchETag(stat.etag())
                        .length(16L)
                        .build())) {
              magic = stream.readNBytes(16);
            }
            if (!validImage(row.get("content_type").toString(), magic))
              throw new IllegalArgumentException("Invalid image bytes");
            minio.copyObject(
                CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(target)
                    .source(
                        CopySource.builder()
                            .bucket(bucket)
                            .object(source)
                            .matchETag(stat.etag())
                            .build())
                    .build());
            jdbc.update(
                "UPDATE file_metadata SET state='READY',object_key=?,actual_size=? WHERE file_id=?",
                target,
                stat.size(),
                id);
            return new FileReference(id, bucket + ":" + target);
          } catch (IllegalArgumentException e) {
            throw e;
          } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload not available", e);
          }
        });
  }

  static boolean validImage(String type, byte[] b) {
    if (b.length < 12) return false;
    return switch (type) {
      case "image/png" ->
          b[0] == (byte) 137
              && b[1] == 80
              && b[2] == 78
              && b[3] == 71
              && b[4] == 13
              && b[5] == 10
              && b[6] == 26
              && b[7] == 10;
      case "image/jpeg" -> b[0] == (byte) 255 && b[1] == (byte) 216 && b[2] == (byte) 255;
      case "image/gif" ->
          new String(b, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a");
      case "image/webp" ->
          new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
              && new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
      default -> false;
    };
  }

  public FileReference confirmed(String id, Long userId, Long noteId) {
    var row = metadata(id, false);
    checkOwner(row, userId);
    if (!"READY".equals(row.get("state"))
        || !Objects.equals(((Number) row.get("note_id")).longValue(), noteId))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Confirmed file for this note required");
    return reference(row);
  }

  private Map<String, Object> metadata(String id, boolean lock) {
    if (id == null) throw new IllegalArgumentException("fileId required");
    var rows =
        jdbc.queryForList(
            "SELECT * FROM file_metadata WHERE file_id=?" + (lock ? " FOR UPDATE" : ""), id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
    return rows.get(0);
  }

  private void checkOwner(Map<String, Object> row, Long userId) {
    if (userId == null || ((Number) row.get("owner_id")).longValue() != userId)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "File owner mismatch");
  }

  private FileReference reference(Map<String, Object> row) {
    return new FileReference(row.get("file_id").toString(), bucket + ":" + row.get("object_key"));
  }

  public Map<String, String> resolve(List<String> keys) {
    if (keys == null || keys.size() > 200) throw new IllegalArgumentException("At most 200 files");
    Map<String, String> result = new HashMap<>();
    for (String key : new HashSet<>(keys)) {
      if (key == null || key.isBlank()) continue;
      String b = legacyBucket, k = key;
      int colon = key.indexOf(':');
      if (colon > 0) {
        b = key.substring(0, colon);
        k = key.substring(colon + 1);
      }
      if (!Set.of(bucket, legacyBucket).contains(b) || k.startsWith("pending/") || k.contains(".."))
        throw new IllegalArgumentException("Invalid file reference");
      try {
        result.put(
            key,
            minio.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(b)
                    .object(k)
                    .expiry(1, TimeUnit.HOURS)
                    .build()));
      } catch (Exception e) {
        throw new IllegalStateException("Cannot resolve file", e);
      }
    }
    return result;
  }
}
