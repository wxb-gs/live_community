package com.example.upload.infrastructure.scheduling;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@EnableScheduling
public class PendingUploadCleanup {
  private final JdbcTemplate jdbc;
  private final MinioClient minio;
  private final String bucket;
  private final TransactionTemplate tx;

  public PendingUploadCleanup(
      JdbcTemplate jdbc,
      MinioClient minio,
      PlatformTransactionManager tm,
      @Value("${minio.bucket:uploads}") String bucket) {
    this.jdbc = jdbc;
    this.minio = minio;
    this.bucket = bucket;
    this.tx = new TransactionTemplate(tm);
  }

  @Scheduled(fixedDelay = 60000)
  public void clean() {
    try {
      tx.executeWithoutResult(
          s -> {
            var rows =
                jdbc.queryForList(
                    "SELECT file_id,owner_id,state FROM file_metadata WHERE temp_removed=0 AND"
                        + " expires_at<? ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED",
                    System.currentTimeMillis() - 3600_000);
            for (var row : rows) {
              try {
                minio.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object("pending/" + row.get("owner_id") + "/" + row.get("file_id"))
                        .build());
              } catch (Exception e) {
                throw new IllegalStateException("Pending cleanup will retry", e);
              }
              jdbc.update(
                  "UPDATE file_metadata SET temp_removed=1,state=CASE WHEN state='PENDING' THEN"
                      + " 'EXPIRED' ELSE state END WHERE file_id=?",
                  row.get("file_id"));
            }
          });
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .error("Pending cleanup failed; metadata retained", e);
    }
  }
}
