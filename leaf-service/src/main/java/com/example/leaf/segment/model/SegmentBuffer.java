package com.example.leaf.segment.model;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SegmentBuffer {

    private String key;
    private final Segment[] segments;
    private volatile int currentPos;
    private volatile boolean nextReady;
    private volatile boolean initOk;
    private final AtomicBoolean threadRunning;
    private final ReadWriteLock lock;

    private volatile int step;
    private volatile long updateTimestamp;

    public SegmentBuffer() {
        this.segments = new Segment[]{new Segment(), new Segment()};
        this.currentPos = 0;
        this.nextReady = false;
        this.initOk = false;
        this.threadRunning = new AtomicBoolean(false);
        this.lock = new ReentrantReadWriteLock();
        this.step = 0;
    }

    public Segment getCurrent() {
        return segments[currentPos];
    }

    public int switchPos() {
        return currentPos == 0 ? 1 : 0;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Segment[] getSegments() { return segments; }
    public int getCurrentPos() { return currentPos; }
    public void setCurrentPos(int currentPos) { this.currentPos = currentPos; }
    public boolean isNextReady() { return nextReady; }
    public void setNextReady(boolean nextReady) { this.nextReady = nextReady; }
    public boolean isInitOk() { return initOk; }
    public void setInitOk(boolean initOk) { this.initOk = initOk; }
    public AtomicBoolean getThreadRunning() { return threadRunning; }
    public ReadWriteLock getLock() { return lock; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public long getUpdateTimestamp() { return updateTimestamp; }
    public void setUpdateTimestamp(long updateTimestamp) { this.updateTimestamp = updateTimestamp; }
}
