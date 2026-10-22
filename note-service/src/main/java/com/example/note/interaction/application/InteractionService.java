package com.example.note.interaction.application;

import com.example.common.*;
import com.example.note.access.TargetAccess;
import com.example.note.content.application.NoteService;
import com.example.outbox.Outbox;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InteractionService {
  private final JdbcTemplate jdbc;
  private final Outbox outbox;
  private final TargetAccess access;

  public InteractionService(JdbcTemplate jdbc, Outbox outbox, TargetAccess access) {
    this.jdbc = jdbc;
    this.outbox = outbox;
    this.access = access;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public ToggleResult set(ToggleRequest req) {
    InteractionType type =
        InteractionType.valueOf(req.getInteractionType().toUpperCase(Locale.ROOT));
    String target = req.getTargetType();
    Long id = req.getTargetId(), user = req.getUserId();
    validate(type, target);
    access.requirePublic(target, id);
    if (user == null || user <= 0)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity required");
    if (req.getDesiredActive() == null
        || req.getRequestId() == null
        || !req.getRequestId().matches("[A-Za-z0-9-]{1,64}"))
      throw new IllegalArgumentException("desiredActive and requestId required");
    String kind = type.name().toLowerCase(Locale.ROOT);
    jdbc.update(
        "INSERT IGNORE INTO interaction_count(target_type,target_id,interaction_type,total,version)"
            + " VALUES(?,?,?,0,0)",
        target,
        id,
        kind);
    var countRow =
        jdbc.queryForMap(
            "SELECT total,version FROM interaction_count WHERE target_type=? AND target_id=? AND"
                + " interaction_type=? FOR UPDATE",
            target,
            id,
            kind);
    String fingerprint = target + ":" + id + ":" + kind + ":" + req.getDesiredActive();
    var previous =
        jdbc.queryForList(
            "SELECT fingerprint,active,total FROM interaction_command WHERE user_id=? AND"
                + " request_id=?",
            user,
            req.getRequestId());
    if (!previous.isEmpty()) {
      var row = previous.get(0);
      if (!fingerprint.equals(row.get("fingerprint")))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "requestId reused for another command");
      return result(
          ((Number) row.get("active")).intValue() == 1,
          ((Number) row.get("total")).longValue(),
          kind);
    }
    var states =
        jdbc.queryForList(
            "SELECT status FROM interaction_record WHERE target_type=? AND target_id=? AND"
                + " interaction_type=? AND user_id=?",
            target,
            id,
            kind,
            user);
    boolean old = !states.isEmpty() && "ACTIVE".equals(states.get(0).get("status"));
    boolean active = req.getDesiredActive();
    long count = ((Number) countRow.get("total")).longValue(),
        version = ((Number) countRow.get("version")).longValue();
    if (old != active) {
      count += active ? 1 : -1;
      if (count < 0) throw new IllegalStateException("Interaction baseline must be reconciled");
      version++;
      long now = System.currentTimeMillis();
      jdbc.update(
          "INSERT INTO"
              + " interaction_record(target_type,target_id,interaction_type,user_id,status,created_at,updated_at)"
              + " VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
              + " status=VALUES(status),updated_at=VALUES(updated_at)",
          target,
          id,
          kind,
          user,
          active ? "ACTIVE" : "INACTIVE",
          now,
          now);
      jdbc.update(
          "UPDATE interaction_count SET total=?,version=? WHERE target_type=? AND target_id=? AND"
              + " interaction_type=?",
          count,
          version,
          target,
          id,
          kind);
      outbox.append(
          "interaction_state",
          target + ":" + id + ":" + kind,
          Map.of(
              "schemaVersion",
              1,
              "eventId",
              UUID.randomUUID().toString(),
              "targetType",
              target,
              "targetId",
              id,
              "interactionType",
              type.name(),
              "count",
              count,
              "version",
              version));
    }
    try {
      jdbc.update(
          "INSERT INTO interaction_command(user_id,request_id,fingerprint,active,total)"
              + " VALUES(?,?,?,?,?)",
          user,
          req.getRequestId(),
          fingerprint,
          active ? 1 : 0,
          count);
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "requestId is already in use", duplicate);
    }
    return result(active, count, kind);
  }

  private ToggleResult result(boolean active, long count, String kind) {
    return new ToggleResult(active, count, (active ? "" : "un") + kind + "d");
  }

  private void validate(InteractionType type, String target) {
    if (!Set.of("note", "comment").contains(target == null ? "" : target)
        || ("comment".equals(target) && type != InteractionType.LIKE))
      throw new IllegalArgumentException("Invalid target/interaction");
  }

  public StatusResult getStatus(InteractionType type, String target, Long id, Long user) {
    validate(type, target);
    access.requirePublic(target, id);
    return batchStatus(type, target, List.of(id), user).get(id);
  }

  public Map<Long, StatusResult> batchStatus(
      InteractionType type, String target, List<Long> ids, Long user) {
    validate(type, target);
    if (ids == null || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("At most 100 ids");
    Map<Long, StatusResult> result = new LinkedHashMap<>();
    if (ids.isEmpty()) return result;
    // Internal bulk read used by content assembly. Public HTTP adapter validates visibility first.
    String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
    List<Object> args = new ArrayList<>(List.of(target, type.name().toLowerCase(Locale.ROOT)));
    args.addAll(ids);
    var counts =
        jdbc.queryForList(
            "SELECT target_id,total FROM interaction_count WHERE target_type=? AND"
                + " interaction_type=? AND target_id IN ("
                + marks
                + ")",
            args.toArray());
    args = new ArrayList<>(List.of(target, type.name().toLowerCase(Locale.ROOT), user));
    args.addAll(ids);
    var active =
        jdbc.queryForList(
            "SELECT target_id FROM interaction_record WHERE target_type=? AND interaction_type=?"
                + " AND user_id=? AND status='ACTIVE' AND target_id IN ("
                + marks
                + ")",
            args.toArray());
    Map<Long, Long> totals = new HashMap<>();
    for (var row : counts)
      totals.put(
          ((Number) row.get("target_id")).longValue(), ((Number) row.get("total")).longValue());
    Set<Long> states = new HashSet<>();
    for (var row : active) states.add(((Number) row.get("target_id")).longValue());
    for (Long id : ids)
      result.put(id, new StatusResult(states.contains(id), totals.getOrDefault(id, 0L)));
    return result;
  }

  public long getCount(InteractionType type, String target, Long id) {
    return batchStatus(type, target, List.of(id), 0L).get(id).count();
  }

  public List<Long> getUserInteractions(
      InteractionType type, String target, Long user, int page, int size) {
    validate(type, target);
    NoteService.page(page, size);
    return jdbc.queryForList(
        "SELECT target_id FROM interaction_record WHERE user_id=? AND target_type=? AND"
            + " interaction_type=? AND status='ACTIVE' ORDER BY updated_at DESC,target_id DESC"
            + " LIMIT ? OFFSET ?",
        Long.class,
        user,
        target,
        type.name().toLowerCase(Locale.ROOT),
        size,
        Math.multiplyExact(page, size));
  }

  public record ToggleResult(boolean active, long count, String action) {}

  public record StatusResult(boolean active, long count) {}
}
