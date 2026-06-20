package com.example.common;

import java.io.Serializable;

public class PresignedUrlResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uploadUrl;
    private String objectKey;
    private long expiresAt;

    public PresignedUrlResponse() {}

    public PresignedUrlResponse(String uploadUrl, String objectKey, long expiresAt) {
        this.uploadUrl = uploadUrl;
        this.objectKey = objectKey;
        this.expiresAt = expiresAt;
    }

    public String getUploadUrl() { return uploadUrl; }
    public void setUploadUrl(String uploadUrl) { this.uploadUrl = uploadUrl; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}
