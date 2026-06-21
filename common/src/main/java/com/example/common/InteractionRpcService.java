package com.example.common;

import java.util.List;

/**
 * 通用互动 RPC 接口 — 点赞、收藏复用同一套逻辑。
 *
 * 设计思路：
 * 1. interactionType 控制行为（LIKE / FAVORITE）
 * 2. targetType 控制目标（note / comment）
 * 3. Redis + Lua 保证原子性，定时同步到 Cassandra
 */
public interface InteractionRpcService {

    /** 点赞/收藏切换（原子操作） */
    ToggleResponse toggle(ToggleRequest request);

    /** 查询单个目标的互动状态 */
    ToggleResponse getStatus(ToggleRequest request);

    /** 批量查询互动状态（Feed 流场景一次查多个笔记的点赞状态） */
    BatchStatusResponse batchStatus(BatchStatusRequest request);

    /** 获取用户互动过的目标ID列表 */
    List<Long> getUserInteractions(String interactionType, String targetType, Long userId, int page, int size);
}
