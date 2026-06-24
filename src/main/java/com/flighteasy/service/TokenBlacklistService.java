package com.flighteasy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    public void blacklist(String accessToken, long remainingMillis) {
        if (remainingMillis <= 0) return;

        String key = BLACKLIST_PREFIX + accessToken;
        stringRedisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(remainingMillis));
        log.info("Access token blacklisted, TTL={}ms", remainingMillis);
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + accessToken));
    }
}
