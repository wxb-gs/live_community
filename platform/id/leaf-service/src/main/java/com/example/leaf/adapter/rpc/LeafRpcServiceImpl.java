package com.example.leaf.adapter.rpc;

import com.example.common.IdResponse;
import com.example.common.LeafRpcService;
import com.example.leaf.application.segment.SegmentIdGenerator;
import com.example.leaf.domain.snowflake.SnowflakeIdGenerator;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class LeafRpcServiceImpl implements LeafRpcService {

  private final SegmentIdGenerator segmentIdGenerator;
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  public LeafRpcServiceImpl(
      SegmentIdGenerator segmentIdGenerator, SnowflakeIdGenerator snowflakeIdGenerator) {
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

  @Override
  public java.util.List<Long> generateSegmentIds(String bizKey, int count) {
    if (count < 1
        || count > 256
        || bizKey == null
        || !java.util.Set.of(
                "note",
                "comment",
                "user",
                "wechat",
                "taobao",
                "phone",
                "user_wechat",
                "user_taobao",
                "user_phone")
            .contains(bizKey)) throw new IllegalArgumentException("Invalid allocation");
    java.util.List<Long> ids = new java.util.ArrayList<>();
    for (int i = 0; i < count; i++) ids.add(segmentIdGenerator.getId(bizKey));
    return ids;
  }
}
