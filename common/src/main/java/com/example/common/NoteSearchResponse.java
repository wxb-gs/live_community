package com.example.common;

import java.io.Serializable;
import java.util.List;

public class NoteSearchResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private long total;
    private int page;
    private int size;
    private List<NoteSearchResult> results;

    public NoteSearchResponse() {}

    public NoteSearchResponse(long total, int page, int size, List<NoteSearchResult> results) {
        this.total = total; this.page = page; this.size = size; this.results = results;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<NoteSearchResult> getResults() { return results; }
    public void setResults(List<NoteSearchResult> results) { this.results = results; }

    public static class NoteSearchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private Long userId;
        private String title;
        private String summary;
        private String tags;
        private String category;
        private int viewCount;
        private int likeCount;
        private Long createdAt;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getViewCount() { return viewCount; }
        public void setViewCount(int viewCount) { this.viewCount = viewCount; }
        public int getLikeCount() { return likeCount; }
        public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
        public Long getCreatedAt() { return createdAt; }
        public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    }
}
