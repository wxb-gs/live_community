package com.example.leaf.segment.model;

import java.util.concurrent.atomic.AtomicLong;

public class Segment {

    private final AtomicLong value;
    private volatile long max;
    private volatile int step;

    public Segment() {
        this.value = new AtomicLong(0);
        this.max = 0;
        this.step = 0;
    }

    public long getIdle() {
        return this.max - this.value.get();
    }

    public long getAndIncrement() {
        return value.getAndIncrement();
    }

    public long getValue() { return value.get(); }
    public void setValue(long value) { this.value.set(value); }
    public long getMax() { return max; }
    public void setMax(long max) { this.max = max; }
    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
}
