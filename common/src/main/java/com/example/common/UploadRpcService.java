package com.example.common;

public interface UploadRpcService {

    PresignedUrlResponse generatePresignedUrl(PresignedUrlRequest request);
}
