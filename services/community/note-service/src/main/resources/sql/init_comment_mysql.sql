-- 评论系统 MySQL 表结构（高性能可扩展设计）
-- 设计思想：索引与内容分离 + 根路径扁平化 + 读写分离

-- 1. 评论主题表：统计和管理被评论对象的状态
CREATE TABLE IF NOT EXISTS comment_subject (
    obj_id     BIGINT  NOT NULL COMMENT '被评论对象ID',
    obj_type   TINYINT NOT NULL COMMENT '对象类型 1:笔记',
    count      INT     NOT NULL DEFAULT 0 COMMENT '总评论数',
    root_count INT     NOT NULL DEFAULT 0 COMMENT '根评论数（一级评论）',
    state      TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0:正常 1:禁止评论',
    PRIMARY KEY (obj_id, obj_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 评论索引表：关系与排序，窄表设计保证 Buffer Pool 缓存效率
CREATE TABLE IF NOT EXISTS comment_index (
    id          BIGINT   NOT NULL COMMENT '评论ID (Leaf号段生成)',
    obj_id      BIGINT   NOT NULL COMMENT '被评论对象ID',
    obj_type    TINYINT  NOT NULL DEFAULT 1 COMMENT '对象类型',
    root_id     BIGINT   NOT NULL DEFAULT 0 COMMENT '根评论ID，0表示自身为根',
    parent_id   BIGINT   NOT NULL DEFAULT 0 COMMENT '直接父评论ID，0表示根评论',
    user_id     BIGINT   NOT NULL COMMENT '发表者ID',
    like_count  INT      NOT NULL DEFAULT 0 COMMENT '点赞数（Redis异步同步）',
    floor       INT      NOT NULL DEFAULT 0 COMMENT '楼层号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    state       TINYINT  NOT NULL DEFAULT 0 COMMENT '状态 0:正常 1:隐藏 2:删除',
    PRIMARY KEY (id),
    -- 核心查询索引：按对象拉取根评论列表（分页）
    INDEX idx_obj_root (obj_id, obj_type, root_id, create_time),
    -- 拉取某个根评论下的所有回复
    INDEX idx_root (root_id, create_time),
    -- 按用户查评论（辅助）
    INDEX idx_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 评论内容表：大文本独立存储，减少索引表 IO 负担
CREATE TABLE IF NOT EXISTS comment_content (
    comment_id BIGINT       NOT NULL COMMENT '对应 comment_index.id',
    message    MEDIUMTEXT        NULL COMMENT '评论正文（支持长文本）',
    extra      VARCHAR(4096)     NULL COMMENT '扩展信息 JSON (IP属地/设备/表情等)',
    PRIMARY KEY (comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
