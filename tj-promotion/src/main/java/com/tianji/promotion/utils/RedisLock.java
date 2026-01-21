package com.tianji.promotion.utils;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@RequiredArgsConstructor
public class RedisLock {

    private String key;

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryLock(long releaseTime, TimeUnit timeUnit) {

        String value = Thread.currentThread().getName();

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, value, releaseTime, timeUnit);

        return Boolean.TRUE.equals(success);
    }

    public void unlock() {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (Thread.currentThread().getName().equals(value)) {
            stringRedisTemplate.delete(key);
        }
    }
}
