package com.example.leafclient;

import com.example.common.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.dubbo.config.annotation.DubboReference;

public class LeafIdClient implements LeafRpcService {
  @DubboReference(check = false, timeout = 3000, retries = 0)
  private LeafRpcService remote;

  private final Map<String, ArrayDeque<Long>> queues = new ConcurrentHashMap<>();

  public IdResponse generateSegmentId(String key) {
    var queue = queues.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (queue) {
      if (queue.isEmpty()) {
        var ids = remote.generateSegmentIds(key, 64);
        if (ids == null
            || ids.size() != 64
            || new HashSet<>(ids).size() != 64
            || ids.stream().anyMatch(v -> v == null || v <= 0))
          throw new IllegalStateException("Invalid leaf batch");
        queue.addAll(ids);
      }
      return new IdResponse(queue.removeFirst(), "segment");
    }
  }

  public java.util.List<Long> generateSegmentIds(String key, int count) {
    return remote.generateSegmentIds(key, count);
  }

  public IdResponse generateSnowflakeId() {
    return remote.generateSnowflakeId();
  }
}
