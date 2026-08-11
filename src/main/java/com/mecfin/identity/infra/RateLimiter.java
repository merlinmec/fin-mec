package com.mecfin.identity.infra;

import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int capacity, Duration window) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacity, window));
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(int capacity, Duration window) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, window))
                .build();
    }
}
