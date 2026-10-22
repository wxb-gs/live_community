package com.example.leafclient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.common.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LeafIdClientTest {
  @Test
  void concurrentCallersShareOneReservedBatch() throws Exception {
    var remote = mock(LeafRpcService.class);
    when(remote.generateSegmentIds("note", 64))
        .thenReturn(LongStream.rangeClosed(1, 64).boxed().toList());
    var client = new LeafIdClient();
    ReflectionTestUtils.setField(client, "remote", remote);
    var pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<Long>> jobs = new ArrayList<>();
      for (int i = 0; i < 64; i++)
        jobs.add(pool.submit(() -> client.generateSegmentId("note").getId()));
      Set<Long> ids = new HashSet<>();
      for (var job : jobs) ids.add(job.get());
      assertEquals(64, ids.size());
      verify(remote, times(1)).generateSegmentIds("note", 64);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void noFallbackIdsAreInventedWhenLeafFails() {
    var remote = mock(LeafRpcService.class);
    when(remote.generateSegmentIds("note", 64))
        .thenThrow(new IllegalStateException("Leaf unavailable"));
    var client = new LeafIdClient();
    ReflectionTestUtils.setField(client, "remote", remote);
    assertThrows(IllegalStateException.class, () -> client.generateSegmentId("note"));
  }
}
