package com.example.upload.rpc;

import com.example.common.PresignedUrlRequest;
import com.example.common.PresignedUrlResponse;
import com.example.common.UploadRpcService;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@DubboService
public class UploadRpcServiceImpl implements UploadRpcService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.presigned-expiry:300}")
    private int presignedExpiry;

    public UploadRpcServiceImpl(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String objectKey = datePath + "/" + UUID.randomUUID() + "_" + request.getFileName();

        try {
            String url = minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(presignedExpiry, TimeUnit.SECONDS)
                            .build());

            return new PresignedUrlResponse(url, objectKey,
                    System.currentTimeMillis() / 1000 + presignedExpiry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
}
