package com.example.note.content.domain;

public class NoteRow {

  private String fileId;
  private long version;

  public String getFileId() {
    return fileId;
  }

  public void setFileId(String v) {
    fileId = v;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long v) {
    version = v;
  }

  private Long id;
  private Long userId;
  private String title;
  private String content;
  private String summary;
  private String tags;
  private String category;
  private String objectKey;
  private long viewCount;
  private long likeCount;
  private String status;
  private long createdAt;
  private long updatedAt;

  public NoteRow() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getTags() {
    return tags;
  }

  public void setTags(String tags) {
    this.tags = tags;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public long getViewCount() {
    return viewCount;
  }

  public void setViewCount(long viewCount) {
    this.viewCount = viewCount;
  }

  public long getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(long likeCount) {
    this.likeCount = likeCount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(long updatedAt) {
    this.updatedAt = updatedAt;
  }
}
