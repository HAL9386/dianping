package com.dp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
  private static final long BEGIN_TIME_STAMP = 1696118400L;
  private static final int COUNT_BITS = 32;
  private final StringRedisTemplate redisTemplate;

  public RedisIdWorker(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public long nextId(String keyPrefix) {
    LocalDateTime now = LocalDateTime.now();
    long nowSecond = now.toEpochSecond(java.time.ZoneOffset.UTC);
    long timeStamp = nowSecond - BEGIN_TIME_STAMP;
    // 用于redis的键，因为redis的值也有上限，同样可以增加一中间层的时间键来扩展
    String dateKey = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
    Long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + dateKey);
    if (count == null) {
      // 几乎不可能发生
      throw new IllegalStateException("Failed to generate ID, Redis increment returned null.");
    }
    return (timeStamp << COUNT_BITS) | count;
  }
}
