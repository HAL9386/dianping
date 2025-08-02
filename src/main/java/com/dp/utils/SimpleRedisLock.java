package com.dp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
  private final String name;
  private final StringRedisTemplate redisTemplate;
  private static final String LOCK_KEY_PREFIX = "lock:";
  // 一个服务器共用一个线程ID前缀
  public static final String THREAD_ID_PREFIX = UUID.randomUUID().toString(true) + "-";

  public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
    this.name = name;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean tryLock(long timeoutSec) {
    String threadId = THREAD_ID_PREFIX + Thread.currentThread().getId();
    Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
    return Boolean.TRUE.equals(success);
  }

  @Override
  public void unlock() {
    String threadId = THREAD_ID_PREFIX + Thread.currentThread().getId();
    String lockKey = LOCK_KEY_PREFIX + this.name;
    if (threadId.equals(redisTemplate.opsForValue().get(lockKey))) {
      redisTemplate.delete(lockKey);
    }
  }
}
