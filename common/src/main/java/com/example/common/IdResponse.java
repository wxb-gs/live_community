package com.example.common;

import java.io.Serializable;

public class IdResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private long id;
    private String mode;

    public IdResponse() {}

    public IdResponse(long id, String mode) {
        this.id = id;
        this.mode = mode;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
