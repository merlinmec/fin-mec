package com.mecfin.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowsUpToCapacityThenBlocksSameKey() {
        RateLimiter rateLimiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume("key", 5, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(rateLimiter.tryConsume("key", 5, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void differentKeysHaveIndependentBuckets() {
        RateLimiter rateLimiter = new RateLimiter();

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume("a", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(rateLimiter.tryConsume("a", 3, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimiter.tryConsume("b", 3, Duration.ofMinutes(1))).isTrue();
    }
}
