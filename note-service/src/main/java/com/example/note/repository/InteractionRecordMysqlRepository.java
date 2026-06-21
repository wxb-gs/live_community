package com.example.note.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * MySQL 互动记录仓库 — 笔记点赞/收藏异步聚合落库。
 * 使用批量 UPSERT（ON DUPLICATE KEY UPDATE）保证幂等。
 */
@Repository
public class InteractionRecordMysqlRepository {

    private static final Logger log = LoggerFactory.getLogger(InteractionRecordMysqlRepository.class);

    private static final String UPSERT_SQL =
            "INSERT INTO interaction_record (target_type, target_id, interaction_type, user_id, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status), updated_at = VALUES(updated_at)";

    private final JdbcTemplate jdbc;

    public InteractionRecordMysqlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 批量合并写入，每批 500 条避免长事务 */
    public void batchUpsert(List<Object[]> batchArgs) {
        if (batchArgs.isEmpty()) return;
        int offset = 0;
        while (offset < batchArgs.size()) {
            int end = Math.min(offset + 500, batchArgs.size());
            List<Object[]> slice = batchArgs.subList(offset, end);
            jdbc.batchUpdate(UPSERT_SQL, slice);
            offset = end;
        }
        log.debug("Batch upserted {} interaction records to MySQL", batchArgs.size());
    }
}
