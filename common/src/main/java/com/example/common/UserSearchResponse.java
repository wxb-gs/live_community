package com.example.common;

import java.io.Serializable;
import java.util.List;

public class UserSearchResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private long total;
    private int page;
    private int size;
    private List<UserSearchResult> results;

    public UserSearchResponse() {}

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public List<UserSearchResult> getResults() { return results; }
    public void setResults(List<UserSearchResult> results) { this.results = results; }

    public static class UserSearchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String username;
        private String nickname;
        private String avatar;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }
}
