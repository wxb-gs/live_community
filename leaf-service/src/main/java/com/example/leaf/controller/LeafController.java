package com.example.leaf.controller;

import com.example.common.IdResponse;
import com.example.leaf.segment.SegmentIdGenerator;
import com.example.leaf.snowflake.SnowflakeIdGenerator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaf")
public class LeafController {

    private final SegmentIdGenerator segmentIdGenerator;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public LeafController(SegmentIdGenerator segmentIdGenerator, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.segmentIdGenerator = segmentIdGenerator;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @GetMapping("/segment")
    public IdResponse getSegmentId(@RequestParam("key") String key) {
        long id = segmentIdGenerator.getId(key);
        return new IdResponse(id, "segment");
    }

    @GetMapping("/snowflake")
    public IdResponse getSnowflakeId() {
        long id = snowflakeIdGenerator.nextId();
        return new IdResponse(id, "snowflake");
    }
}
