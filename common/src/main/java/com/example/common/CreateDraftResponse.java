package com.example.common;

import java.io.Serializable;

public class CreateDraftResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long noteId;
    private String status;

    public CreateDraftResponse() {}

    public CreateDraftResponse(Long noteId, String status) {
        this.noteId = noteId;
        this.status = status;
    }

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
