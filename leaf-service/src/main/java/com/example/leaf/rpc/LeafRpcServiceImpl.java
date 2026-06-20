package com.example.leaf.rpc;

import com.example.common.IdResponse;
import com.example.common.LeafRpcService;
import com.example.leaf.segment.SegmentIdGenerator;
import com.example.leaf.snowflake.SnowflakeIdGenerator;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class LeafRpcServiceImpl implements LeafRpcService {

    private final SegmentIdGenerator segmentIdGenerator;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public LeafRpcServiceImpl(SegmentIdGenerator segmentIdGenerator, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.segmentIdGenerator = segmentIdGenerator;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Override
    public IdResponse generateSegmentId(String bizKey) {
        long id = segmentIdGenerator.getId(bizKey);
        return new IdResponse(id, "segment");
    }

    @Override
    public IdResponse generateSnowflakeId() {
        long id = snowflakeIdGenerator.nextId();
        return new IdResponse(id, "snowflake");
    }
}
