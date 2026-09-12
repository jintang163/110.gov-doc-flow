package com.gov.gw.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.token-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenStore implements TokenStore {
    private record Entry(Long userId, long expireAt) {}

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    @Override
    public void put(String token, Long userId, long ttlSeconds) {
        tokens.put(token, new Entry(userId, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    @Override
    public Optional<Long> get(String token) {
        Entry e = tokens.get(token);
        if (e == null) return Optional.empty();
        if (e.expireAt() < System.currentTimeMillis()) {
            tokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(e.userId());
    }

    @Override
    public void remove(String token) {
        tokens.remove(token);
    }
}
