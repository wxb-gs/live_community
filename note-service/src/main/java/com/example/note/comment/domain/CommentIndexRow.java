package com.example.note.comment.domain;

import java.time.LocalDateTime;

/** 评论索引表 POJO — 对应 comment_index 表。 窄表设计，存储评论的元数据和层级关系，用于分页查询和排序。 */
public class CommentIndexRow {

  private Long id;
  private Long objId;
  private Integer objType;
  private Long rootId;
  private Long parentId;
  private Long userId;
  private Long likeCount;
  private Integer floor;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
  private Integer state;

  public CommentIndexRow() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

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

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(Long likeCount) {
    this.likeCount = likeCount;
  }

  public Integer getFloor() {
    return floor;
  }

  public void setFloor(Integer floor) {
    this.floor = floor;
  }

  public LocalDateTime getCreateTime() {
    return createTime;
  }

  public void setCreateTime(LocalDateTime createTime) {
    this.createTime = createTime;
  }

  public LocalDateTime getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(LocalDateTime updateTime) {
    this.updateTime = updateTime;
  }

  public Integer getState() {
    return state;
  }

  public void setState(Integer state) {
    this.state = state;
  }

  public boolean isRoot() {
    return rootId == 0;
  }
}
