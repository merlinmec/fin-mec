package com.mecfin.identity.infra;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    // Callers (unauthenticated) control part of the cache key (IP+email on /auth/login), so a
    // plain unbounded ConcurrentHashMap would let an attacker exhaust heap by cycling through
    // distinct emails. Caffeine bounds memory with a size cap and evicts idle buckets after
    // they've gone untouched well past any realistic rate-limit window.
    private final ConcurrentMap<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(50_000)
            .<String, Bucket>build()
            .asMap();

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
