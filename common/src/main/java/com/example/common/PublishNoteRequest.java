package com.example.common;

import java.io.Serializable;

public class PublishNoteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long noteId;
    private String fileName;
    private String contentType;

    public PublishNoteRequest() {}

    public PublishNoteRequest(Long noteId, String fileName, String contentType) {
        this.noteId = noteId;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
