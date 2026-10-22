CREATE TABLE IF NOT EXISTS interaction_record (
    target_type      VARCHAR(32)  NOT NULL COMMENT '目标类型: note',
    target_id        BIGINT       NOT NULL COMMENT '目标ID',
    interaction_type VARCHAR(32)  NOT NULL COMMENT '互动类型: like, favorite',
    user_id          BIGINT       NOT NULL COMMENT '用户ID',
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, INACTIVE',
    created_at       BIGINT       NOT NULL COMMENT '创建时间戳(ms)',
    updated_at       BIGINT       NOT NULL COMMENT '更新时间戳(ms)',
    PRIMARY KEY (target_type, target_id, interaction_type, user_id),
    INDEX idx_user_interaction (user_id, target_type, interaction_type),
    INDEX idx_target (target_type, target_id, interaction_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
