package com.example.common;

/**
 * 互动类型枚举 — 点赞、收藏、以及未来可扩展的互动行为。
 * 配合 {@link InteractionRpcService} 使用，一套逻辑复用所有互动类型。
 */
public enum InteractionType {
    LIKE,
    FAVORITE;
}
