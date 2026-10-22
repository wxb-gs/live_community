package com.example.common;

import java.io.Serializable;

public class CommentRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  private Long noteId;
  private Long userId;
  private String content;

  /** 父评论ID，0 表示根评论 */
  private Long parentId;

  /** 根评论ID，0 表示自身为根 */
  private Long rootId;

  public CommentRequest() {}

  public CommentRequest(Long noteId, Long userId, String content) {
    this.noteId = noteId;
    this.userId = userId;
    this.content = content;
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

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public Long getRootId() {
    return rootId;
  }

  public void setRootId(Long rootId) {
    this.rootId = rootId;
  }

  /** 是否为根评论（非回复） */
  public boolean isRoot() {
    return parentId == null || parentId == 0;
  }
}
