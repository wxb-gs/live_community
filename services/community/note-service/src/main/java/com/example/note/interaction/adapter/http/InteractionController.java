package com.example.note.interaction.adapter.http;

import com.example.common.*;
import com.example.note.interaction.application.InteractionApplicationService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interaction")
public class InteractionController {

  private final InteractionApplicationService interactionRpcService;
  private final com.example.note.access.TargetAccess access;

  public InteractionController(
      InteractionApplicationService service, com.example.note.access.TargetAccess access) {
    this.interactionRpcService = service;
    this.access = access;
  }

  @PostMapping("/toggle")
  public Result<ToggleResponse> toggle(
      @AuthenticationPrincipal Long userId, @RequestBody ToggleRequest request) {
    request.setUserId(userId);
    ToggleResponse resp = interactionRpcService.toggle(request);
    return Result.ok(resp);
  }

  @GetMapping("/status")
  public Result<ToggleResponse> getStatus(
      @AuthenticationPrincipal Long userId,
      @RequestParam String interactionType,
      @RequestParam String targetType,
      @RequestParam Long targetId) {
    ToggleRequest req = new ToggleRequest(interactionType, targetType, targetId, userId);
    ToggleResponse resp = interactionRpcService.getStatus(req);
    return Result.ok(resp);
  }

  @PostMapping("/batch-status")
  public Result<BatchStatusResponse> batchStatus(
      @AuthenticationPrincipal Long userId, @RequestBody BatchStatusRequest request) {
    if (request.getTargetIds() == null || request.getTargetIds().size() > 100)
      throw new IllegalArgumentException("At most 100 targets");
    request.getTargetIds().forEach(id -> access.requirePublic(request.getTargetType(), id));
    request.setUserId(userId);
    BatchStatusResponse resp = interactionRpcService.batchStatus(request);
    return Result.ok(resp);
  }

  @GetMapping("/user-interactions")
  public Result<List<Long>> getUserInteractions(
      @AuthenticationPrincipal Long userId,
      @RequestParam String interactionType,
      @RequestParam String targetType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    List<Long> ids =
        interactionRpcService.getUserInteractions(interactionType, targetType, userId, page, size);
    return Result.ok(ids);
  }
}
