package com.gov.gw.auth;

import java.util.Optional;

/** 登录令牌存储。单机用内存实现，集群部署切换 Redis 实现（app.token-store=redis） */
public interface TokenStore {
    void put(String token, Long userId, long ttlSeconds);
    Optional<Long> get(String token);
    void remove(String token);
}
