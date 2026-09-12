package com.gov.gw.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.token-store", havingValue = "redis")
public class RedisTokenStore implements TokenStore {
    private static final String PREFIX = "gw:token:";
    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String token, Long userId, long ttlSeconds) {
        redis.opsForValue().set(PREFIX + token, String.valueOf(userId), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<Long> get(String token) {
        String v = redis.opsForValue().get(PREFIX + token);
        return v == null ? Optional.empty() : Optional.of(Long.parseLong(v));
    }

    @Override
    public void remove(String token) {
        redis.delete(PREFIX + token);
    }
}
