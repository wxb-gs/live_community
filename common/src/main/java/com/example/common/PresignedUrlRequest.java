package com.example.common;

import java.io.Serializable;

public class PresignedUrlRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fileName;
    private String contentType;

    public PresignedUrlRequest() {}

    public PresignedUrlRequest(String fileName, String contentType) {
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
