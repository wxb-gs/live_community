package com.example.note.comment.application;

import com.example.common.*;
import com.example.note.access.TargetAccess;
import com.example.note.comment.domain.CommentIndexRow;
import com.example.note.comment.infrastructure.CommentContentRepository;
import com.example.note.comment.infrastructure.CommentIndexRepository;
import com.example.note.comment.infrastructure.CommentSubjectRepository;
import com.example.note.content.application.NoteService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 评论服务 — MySQL 版，索引/内容分离 + 根路径扁平化。
 *
 * <p>读路径：comment_index（窄表分页）→ comment_content（批量加载文本） 写路径：Leaf 生成 ID → index + content 双写 → subject
 * 计数器更新 点赞：Redis + Kafka → 异步批量同步到 comment_index.like_count
 */
@Service
public class CommentService {

  private static final Logger log = LoggerFactory.getLogger(CommentService.class);

  /** 根评论下预加载的子回复数（盖楼折叠） */
  private static final int PREVIEW_REPLIES_LIMIT = 3;

  private static final int OBJ_TYPE_NOTE = 1;

  private final TargetAccess access;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final CommentIndexRepository indexRepo;
  private final CommentContentRepository contentRepo;
  private final CommentSubjectRepository subjectRepo;

  @org.springframework.beans.factory.annotation.Autowired
  private com.example.leafclient.LeafIdClient leafRpcService;

  public CommentService(
      CommentIndexRepository indexRepo,
      CommentContentRepository contentRepo,
      CommentSubjectRepository subjectRepo,
      TargetAccess access,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.access = access;
    this.jdbc = jdbc;
    this.indexRepo = indexRepo;
    this.contentRepo = contentRepo;
    this.subjectRepo = subjectRepo;
  }

  // ═══════════════════════════════════════════════════════════════
  // 发表评论
  // ═══════════════════════════════════════════════════════════════

  /**
   * 发表评论（支持根评论和回复）。 1. Leaf 生成分布式 ID 2. 写入 comment_index + comment_content 3. 更新 comment_subject 计数
   */
  @org.springframework.transaction.annotation.Transactional(
      isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public CommentResponse addComment(CommentRequest request) {
    access.requirePublic("note", request.getNoteId());
    if (request.getUserId() == null
        || request.getContent() == null
        || request.getContent().isBlank()
        || request.getContent().length() > 5000)
      throw new IllegalArgumentException("Valid comment and identity required");
    IdResponse idResp = leafRpcService.generateSegmentId("comment");
    long commentId = idResp.getId();
    Long noteId = request.getNoteId();

    boolean isRoot = request.isRoot();
    long rootId =
        isRoot
            ? 0
            : (request.getRootId() != null && request.getRootId() > 0
                ? request.getRootId()
                : request.getParentId());
    long parentId = request.getParentId() != null ? request.getParentId() : 0;

    if (!isRoot) {
      var parent =
          indexRepo
              .findById(parentId)
              .orElseThrow(() -> new IllegalArgumentException("Parent missing"));
      if (!Objects.equals(parent.getObjId(), noteId) || parent.getState() != 0)
        throw new IllegalArgumentException("Parent belongs to another note or is hidden");
      rootId = parent.isRoot() ? parent.getId() : parent.getRootId();
      var root =
          indexRepo
              .findById(rootId)
              .orElseThrow(() -> new IllegalArgumentException("Root missing"));
      if (!root.isRoot() || !Objects.equals(root.getObjId(), noteId) || root.getState() != 0)
        throw new IllegalArgumentException("Invalid root");
    }
    // 回复时验证父评论存在且为根评论
    if (!isRoot && rootId != parentId) {
      Optional<CommentIndexRow> parentRow = indexRepo.findById(parentId);
      if (parentRow.isEmpty()) {
        throw new RuntimeException("Parent comment not found: " + parentId);
      }
      // 如果回复的是子评论，root_id 跟随其 root_id
      if (!parentRow.get().isRoot()) {
        rootId = parentRow.get().getRootId();
      }
    }

    // 确保 subject 行存在
    subjectRepo.ensureSubject(noteId, OBJ_TYPE_NOTE);

    jdbc.queryForMap(
        "SELECT * FROM comment_subject WHERE obj_id=? AND obj_type=? FOR UPDATE",
        noteId,
        OBJ_TYPE_NOTE);
    // 计算楼层（仅根评论有楼层号）
    int floor = 0;
    if (isRoot) {
      floor = indexRepo.getMaxFloor(noteId, OBJ_TYPE_NOTE) + 1;
    }

    LocalDateTime now = LocalDateTime.now();

    // 写入索引表
    CommentIndexRow indexRow = new CommentIndexRow();
    indexRow.setId(commentId);
    indexRow.setObjId(noteId);
    indexRow.setObjType(OBJ_TYPE_NOTE);
    indexRow.setRootId(rootId);
    indexRow.setParentId(parentId);
    indexRow.setUserId(request.getUserId());
    indexRow.setFloor(floor);
    indexRow.setCreateTime(now);
    indexRow.setState(0);
    indexRepo.insert(indexRow);

    // 写入内容表
    contentRepo.insert(commentId, request.getContent(), null);

    // 更新计数器
    if (isRoot) {
      subjectRepo.incrementRootCount(noteId, OBJ_TYPE_NOTE);
    } else {
      subjectRepo.incrementCount(noteId, OBJ_TYPE_NOTE);
    }

    log.info(
        "Comment added: id={}, noteId={}, rootId={}, parentId={}, floor={}",
        commentId,
        noteId,
        rootId,
        parentId,
        floor);

    CommentResponse resp =
        new CommentResponse(
            commentId, noteId, request.getUserId(), request.getContent(), toEpochMillis(now));
    resp.setRootId(isRoot ? 0 : rootId);
    resp.setParentId(parentId);
    resp.setFloor(floor);
    resp.setState(0);
    return resp;
  }

  // ═══════════════════════════════════════════════════════════════
  // 读取根评论列表（分页）
  // ═══════════════════════════════════════════════════════════════

  /** 分页拉取根评论列表（含前 3 条子回复预览）。 查询路径： 1. idx_obj_root 索引 → 根评论分页 2. 批量加载根评论内容 3. 每条根评论拉取前 3 条子回复预览 */
  public List<CommentResponse> listRootComments(Long noteId, int page, int size) {
    access.requirePublic("note", noteId);
    NoteService.page(page, size);
    int offset = page * size;
    List<CommentIndexRow> rootRows =
        indexRepo.findRootComments(noteId, OBJ_TYPE_NOTE, size, offset);
    if (rootRows.isEmpty()) return Collections.emptyList();

    // 批量加载根评论内容
    List<Long> rootIds = rootRows.stream().map(CommentIndexRow::getId).toList();
    Map<Long, String> contentMap = contentRepo.batchGetContent(rootIds);

    // 组装根评论列表
    List<CommentResponse> roots =
        rootRows.stream()
            .map(row -> toCommentResponse(row, contentMap.get(row.getId())))
            .collect(Collectors.toList());

    // 为每条根评论加载前 3 条子回复
    for (CommentResponse root : roots) {
      List<CommentIndexRow> previewRows =
          indexRepo.findPreviewReplies(root.getCommentId(), PREVIEW_REPLIES_LIMIT);
      if (!previewRows.isEmpty()) {
        List<Long> previewIds = previewRows.stream().map(CommentIndexRow::getId).toList();
        Map<Long, String> previewContent = contentRepo.batchGetContent(previewIds);
        List<CommentResponse> previews =
            previewRows.stream()
                .map(row -> toCommentResponse(row, previewContent.get(row.getId())))
                .collect(Collectors.toList());
        root.setPreviewReplies(previews);
      }
      // 查询回复总数（用于"查看更多"按钮）
      int replyCount = indexRepo.countReplies(root.getCommentId());
      root.setReplyCount(replyCount);
    }

    return roots;
  }

  // ═══════════════════════════════════════════════════════════════
  // 读取子回复列表（分页）
  // ═══════════════════════════════════════════════════════════════

  /** 分页拉取某条根评论下的子回复列表。 查询路径： 1. idx_root 索引 → 子回复分页 2. 批量加载内容 */
  public List<CommentResponse> listReplies(Long rootId, int page, int size) {
    access.requirePublic("comment", rootId);
    NoteService.page(page, size);
    int offset = page * size;
    List<CommentIndexRow> replyRows = indexRepo.findReplies(rootId, size, offset);
    if (replyRows.isEmpty()) return Collections.emptyList();

    List<Long> replyIds = replyRows.stream().map(CommentIndexRow::getId).toList();
    Map<Long, String> contentMap = contentRepo.batchGetContent(replyIds);

    return replyRows.stream()
        .map(row -> toCommentResponse(row, contentMap.get(row.getId())))
        .collect(Collectors.toList());
  }

  // ═══════════════════════════════════════════════════════════════
  // 单条评论详情
  // ═══════════════════════════════════════════════════════════════

  public Optional<CommentResponse> getById(Long commentId) {
    return indexRepo
        .findById(commentId)
        .map(
            row -> {
              String content = contentRepo.findContent(commentId).orElse("");
              return toCommentResponse(row, content);
            });
  }

  // ═══════════════════════════════════════════════════════════════
  // 内容映射（按索引行批量返回 content 文本）
  // ═══════════════════════════════════════════════════════════════

  /** 根据索引行列表批量加载内容，返回 commentId → CommentResponse 映射（无层级信息） */
  public Map<Long, CommentResponse> batchGetContent(List<CommentIndexRow> rows) {
    if (rows.isEmpty()) return Collections.emptyMap();
    List<Long> ids = rows.stream().map(CommentIndexRow::getId).toList();
    Map<Long, String> contentMap = contentRepo.batchGetContent(ids);
    Map<Long, CommentResponse> result = new LinkedHashMap<>();
    for (CommentIndexRow row : rows) {
      result.put(row.getId(), toCommentResponse(row, contentMap.get(row.getId())));
    }
    return result;
  }

  // ═══════════════════════════════════════════════════════════════
  // 映射工具
  // ═══════════════════════════════════════════════════════════════

  private CommentResponse toCommentResponse(CommentIndexRow row, String content) {
    CommentResponse resp =
        new CommentResponse(
            row.getId(),
            row.getObjId(),
            row.getUserId(),
            content,
            toEpochMillis(row.getCreateTime()));
    resp.setRootId(row.getRootId());
    resp.setParentId(row.getParentId());
    resp.setFloor(row.getFloor());
    resp.setLikeCount(row.getLikeCount() != null ? row.getLikeCount().longValue() : 0L);
    resp.setState(row.getState());
    return resp;
  }

  private static long toEpochMillis(LocalDateTime dt) {
    return dt.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
  }
}
