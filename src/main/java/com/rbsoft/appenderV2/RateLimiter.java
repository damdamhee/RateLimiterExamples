package com.rbsoft.appenderV2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimiter {
    private final long timeoutNanos;
    private final int permits;
    private final Lock lock;
    private long lastProcessTime;
    private long availablePermits;

    public RateLimiter(long timeoutNanos, int permits) {
        this.timeoutNanos = timeoutNanos;
        this.permits = permits;
        this.lock = new ReentrantLock(true);
        this.lastProcessTime = System.nanoTime();
        this.availablePermits = permits;
    }

    public boolean acquire() {
        this.lock.lock();
        long now = System.nanoTime();
        try {
            if (isExpired(now)) { //마지막 시간으로부터 1초가 이미 지난 경우
                this.lastProcessTime = now;
                this.availablePermits = permits;
                return true;
            } else if (0 < this.availablePermits) { //가용한 permit이 있는 경우.
                this.lastProcessTime = now;
                this.availablePermits--;
                return true;
            } else return false;
        } finally {
            this.lock.unlock();
        }
    }

    public boolean isExpired(long now) {
        long elapsed = now - this.lastProcessTime;
        return this.timeoutNanos <= elapsed;
    }
}
