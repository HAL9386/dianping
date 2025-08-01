package com.dp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
  private final String name;
  private final StringRedisTemplate redisTemplate;
  private static final String LOCK_KEY_PREFIX = "lock:";

  public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
    this.name = name;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean tryLock(long timeoutSec) {
    long threadId = Thread.currentThread().getId();
    Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + name, Long.toString(threadId), timeoutSec, TimeUnit.SECONDS);
    return Boolean.TRUE.equals(success);
  }

  @Override
  public void unlock() {
    redisTemplate.delete(LOCK_KEY_PREFIX + this.name);
  }
}
