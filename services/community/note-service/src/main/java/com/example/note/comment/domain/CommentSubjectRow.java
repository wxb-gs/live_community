package com.example.note.comment.domain;

/** 评论主题表 POJO — 对应 comment_subject 表。 记录每个被评论对象的统计信息和状态。 */
public class CommentSubjectRow {

  private Long objId;
  private Integer objType;
  private Integer count;
  private Integer rootCount;
  private Integer state;

  public CommentSubjectRow() {}

  public Long getObjId() {
    return objId;
  }

  public void setObjId(Long objId) {
    this.objId = objId;
  }

  public Integer getObjType() {
    return objType;
  }

  public void setObjType(Integer objType) {
    this.objType = objType;
  }

  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  public Integer getRootCount() {
    return rootCount;
  }

  public void setRootCount(Integer rootCount) {
    this.rootCount = rootCount;
  }

  public Integer getState() {
    return state;
  }

  public void setState(Integer state) {
    this.state = state;
  }
}
