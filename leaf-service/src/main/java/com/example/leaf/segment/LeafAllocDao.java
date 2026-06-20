package com.example.leaf.segment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class LeafAllocDao {

    private static final Logger log = LoggerFactory.getLogger(LeafAllocDao.class);

    private static final String QUERY_SQL = "SELECT biz_tag, max_id, step FROM leaf_alloc WHERE biz_tag = ?";
    private static final String UPDATE_SQL = "UPDATE leaf_alloc SET max_id = max_id + step WHERE biz_tag = ?";

    private final JdbcTemplate jdbcTemplate;

    public LeafAllocDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SegmentAllocResult updateAndGet(String bizKey) {
        int rows = jdbcTemplate.update(UPDATE_SQL, bizKey);
        if (rows == 0) {
            log.error("No leaf_alloc row found for biz_tag={}", bizKey);
            return null;
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(QUERY_SQL, bizKey);
        long maxId = ((Number) row.get("max_id")).longValue();
        int step = ((Number) row.get("step")).intValue();
        return new SegmentAllocResult(maxId, step);
    }

    public record SegmentAllocResult(long maxId, int step) {}
}
