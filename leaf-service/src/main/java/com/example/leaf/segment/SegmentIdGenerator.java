package com.example.leaf.segment;

import com.example.leaf.segment.model.Segment;
import com.example.leaf.segment.model.SegmentBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class SegmentIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SegmentIdGenerator.class);

    private final LeafAllocDao leafAllocDao;
    private final Map<String, SegmentBuffer> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "leaf-segment-buffer-loader");
        t.setDaemon(true);
        return t;
    });

    public SegmentIdGenerator(LeafAllocDao leafAllocDao) {
        this.leafAllocDao = leafAllocDao;
    }

    public long getId(String bizKey) {
        SegmentBuffer buffer = cache.computeIfAbsent(bizKey, this::initSegmentBuffer);
        if (!buffer.isInitOk()) {
            synchronized (buffer) {
                if (!buffer.isInitOk()) {
                    updateSegmentFromDb(bizKey, buffer.getCurrent());
                    buffer.setInitOk(true);
                }
            }
        }
        return getFromBuffer(bizKey, buffer);
    }

    private SegmentBuffer initSegmentBuffer(String key) {
        SegmentBuffer buffer = new SegmentBuffer();
        buffer.setKey(key);
        return buffer;
    }

    private long getFromBuffer(String bizKey, SegmentBuffer buffer) {
        while (true) {
            buffer.getLock().readLock().lock();
            try {
                Segment segment = buffer.getCurrent();
                if (!buffer.isNextReady()
                        && segment.getIdle() < 0.9 * segment.getStep()
                        && buffer.getThreadRunning().compareAndSet(false, true)) {
                    scheduler.execute(() -> {
                        try {
                            loadNextSegment(bizKey, buffer);
                        } finally {
                            buffer.getThreadRunning().set(false);
                        }
                    });
                }

                long value = segment.getAndIncrement();
                if (value < segment.getMax()) {
                    return value;
                }
            } finally {
                buffer.getLock().readLock().unlock();
            }

            waitAndSwitch(buffer, bizKey);
        }
    }

    private void loadNextSegment(String bizKey, SegmentBuffer buffer) {
        Segment segment = buffer.getSegments()[buffer.switchPos()];
        boolean loaded = updateSegmentFromDb(bizKey, segment);
        if (loaded) {
            buffer.getLock().writeLock().lock();
            try {
                buffer.setNextReady(true);
            } finally {
                buffer.getLock().writeLock().unlock();
            }
        }
    }

    private void waitAndSwitch(SegmentBuffer buffer, String bizKey) {
        buffer.getLock().writeLock().lock();
        try {
            Segment segment = buffer.getCurrent();
            long value = segment.getValue();
            if (value < segment.getMax()) {
                return;
            }

            if (buffer.isNextReady()) {
                buffer.setCurrentPos(buffer.switchPos());
                buffer.setNextReady(false);
            } else {
                log.warn("Both segments exhausted for key={}, loading synchronously", bizKey);
                updateSegmentFromDb(bizKey, segment);
            }
        } finally {
            buffer.getLock().writeLock().unlock();
        }
    }

    private boolean updateSegmentFromDb(String bizKey, Segment segment) {
        try {
            LeafAllocDao.SegmentAllocResult result = leafAllocDao.updateAndGet(bizKey);
            if (result == null) {
                log.error("No leaf_alloc row found for biz_tag={}", bizKey);
                return false;
            }
            segment.setValue(result.maxId() - result.step());
            segment.setMax(result.maxId());
            segment.setStep(result.step());
            return true;
        } catch (Exception e) {
            log.error("Failed to update segment from db for key={}", bizKey, e);
            return false;
        }
    }
}
