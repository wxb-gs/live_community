package com.example.note.comment.domain;

/** 评论内容表 POJO — 对应 comment_content 表。 大文本独立存储，仅在需要展示内容时查询。 */
public class CommentContentRow {

  private Long commentId;
  private String message;
  private String extra;

  public CommentContentRow() {}

  public Long getCommentId() {
    return commentId;
  }

  public void setCommentId(Long commentId) {
    this.commentId = commentId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getExtra() {
    return extra;
  }

  public void setExtra(String extra) {
    this.extra = extra;
  }
}
