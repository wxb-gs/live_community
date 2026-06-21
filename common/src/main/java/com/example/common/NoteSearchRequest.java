package com.example.common;

import java.io.Serializable;

public class NoteSearchRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String q;
    private int page = 1;
    private int size = 20;
    private String category;
    private String sort = "relevance";

    public NoteSearchRequest() {}

    public NoteSearchRequest(String q, int page, int size, String category, String sort) {
        this.q = q; this.page = page; this.size = size; this.category = category; this.sort = sort;
    }

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
