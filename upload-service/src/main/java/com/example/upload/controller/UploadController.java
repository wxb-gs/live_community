package com.example.upload.controller;

import com.example.common.Result;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.presigned-expiry:300}")
    private int presignedExpiry;

    public UploadController(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @GetMapping("/presigned")
    public Result<Map<String, Object>> presignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType) {

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = datePath + "/" + UUID.randomUUID() + "_" + fileName;

        try {
            String url = minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(presignedExpiry, TimeUnit.SECONDS)
                            .build());

            Map<String, Object> data = new HashMap<>();
            data.put("uploadUrl", url);
            data.put("objectKey", objectKey);
            data.put("expiresAt", System.currentTimeMillis() / 1000 + presignedExpiry);

            return Result.ok(data);
        } catch (Exception e) {
            return Result.error(500, "Failed to generate presigned URL: " + e.getMessage());
        }
    }
}
