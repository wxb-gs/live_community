package com.example.note.rpc;

import com.example.common.*;
import com.example.note.service.InteractionService;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@DubboService
public class InteractionRpcServiceImpl implements InteractionRpcService {

    private final InteractionService interactionService;

    public InteractionRpcServiceImpl(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @Override
    public ToggleResponse toggle(ToggleRequest request) {
        InteractionType type = InteractionType.valueOf(request.getInteractionType().toUpperCase());
        InteractionService.ToggleResult result = interactionService.toggle(
                type, request.getTargetType(), request.getTargetId(), request.getUserId());
        return new ToggleResponse(result.active(), result.count(), result.action());
    }

    @Override
    public ToggleResponse getStatus(ToggleRequest request) {
        InteractionType type = InteractionType.valueOf(request.getInteractionType().toUpperCase());
        InteractionService.StatusResult result = interactionService.getStatus(
                type, request.getTargetType(), request.getTargetId(), request.getUserId());
        return new ToggleResponse(result.active(), result.count(),
                result.active() ? request.getInteractionType().toLowerCase() + "d" : "un" + request.getInteractionType().toLowerCase() + "d");
    }

    @Override
    public BatchStatusResponse batchStatus(BatchStatusRequest request) {
        InteractionType type = InteractionType.valueOf(request.getInteractionType().toUpperCase());
        Map<Long, InteractionService.StatusResult> results = interactionService.batchStatus(
                type, request.getTargetType(), request.getTargetIds(), request.getUserId());
        Map<Long, BatchStatusResponse.TargetStatus> statuses = results.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new BatchStatusResponse.TargetStatus(e.getValue().active(), e.getValue().count())));
        return new BatchStatusResponse(statuses);
    }

    @Override
    public List<Long> getUserInteractions(String interactionType, String targetType, Long userId, int page, int size) {
        InteractionType type = InteractionType.valueOf(interactionType.toUpperCase());
        return interactionService.getUserInteractions(type, targetType, userId, page, size);
    }
}
