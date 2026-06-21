package com.example.gateway.controller;

import com.example.common.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interaction")
public class InteractionController {

    @DubboReference(check = false)
    private InteractionRpcService interactionRpcService;

    @PostMapping("/toggle")
    public Result<ToggleResponse> toggle(@AuthenticationPrincipal Long userId,
                                         @RequestBody ToggleRequest request) {
        request.setUserId(userId);
        ToggleResponse resp = interactionRpcService.toggle(request);
        return Result.ok(resp);
    }

    @GetMapping("/status")
    public Result<ToggleResponse> getStatus(@AuthenticationPrincipal Long userId,
                                            @RequestParam String interactionType,
                                            @RequestParam String targetType,
                                            @RequestParam Long targetId) {
        ToggleRequest req = new ToggleRequest(interactionType, targetType, targetId, userId);
        ToggleResponse resp = interactionRpcService.getStatus(req);
        return Result.ok(resp);
    }

    @PostMapping("/batch-status")
    public Result<BatchStatusResponse> batchStatus(@AuthenticationPrincipal Long userId,
                                                    @RequestBody BatchStatusRequest request) {
        request.setUserId(userId);
        BatchStatusResponse resp = interactionRpcService.batchStatus(request);
        return Result.ok(resp);
    }

    @GetMapping("/user-interactions")
    public Result<List<Long>> getUserInteractions(@AuthenticationPrincipal Long userId,
                                                   @RequestParam String interactionType,
                                                   @RequestParam String targetType,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        List<Long> ids = interactionRpcService.getUserInteractions(interactionType, targetType, userId, page, size);
        return Result.ok(ids);
    }
}
