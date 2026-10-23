package com.example.common;

import java.io.Serializable;
import java.util.List;

public class CommentResponse implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long commentId;
  private Long noteId;
  private Long userId;
  private String content;
  private Long createdAt;
  private Long likeCount;

  // -- 嵌套评论字段 --
  private Long rootId; // 根评论ID，0 表示自身为根
  private Long parentId; // 直接父评论ID
  private Integer floor; // 楼层号
  private Integer replyCount; // 子回复总数
  private Integer state; // 状态 0:正常 1:隐藏 2:删除

  /** 前N条子回复预览（盖楼折叠模式） */
  private List<CommentResponse> previewReplies;

  public CommentResponse() {}

  public CommentResponse(Long commentId, Long noteId, Long userId, String content, Long createdAt) {
    this.commentId = commentId;
    this.noteId = noteId;
    this.userId = userId;
    this.content = content;
    this.createdAt = createdAt;
  }

  public Long getCommentId() {
    return commentId;
  }

  public void setCommentId(Long commentId) {
    this.commentId = commentId;
  }

  public Long getNoteId() {
    return noteId;
  }

  public void setNoteId(Long noteId) {
    this.noteId = noteId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
  }

  public Long getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(Long likeCount) {
    this.likeCount = likeCount;
  }

  public Long getRootId() {
    return rootId;
  }

  public void setRootId(Long rootId) {
    this.rootId = rootId;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public Integer getFloor() {
    return floor;
  }

  public void setFloor(Integer floor) {
    this.floor = floor;
  }

  public Integer getReplyCount() {
    return replyCount;
  }

  public void setReplyCount(Integer replyCount) {
    this.replyCount = replyCount;
  }

  public Integer getState() {
    return state;
  }

  public void setState(Integer state) {
    this.state = state;
  }

  public List<CommentResponse> getPreviewReplies() {
    return previewReplies;
  }

  public void setPreviewReplies(List<CommentResponse> previewReplies) {
    this.previewReplies = previewReplies;
  }

  public boolean isRoot() {
    return rootId == null || rootId == 0;
  }
}
