package com.dp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dp.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheUtil {
  private final StringRedisTemplate redisTemplate;
  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

  public CacheUtil(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void set(String key, Object value, Long time, TimeUnit unit) {
    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
  }

  /**
   * 查询数据并解决缓存穿透
   *
   * @param keyPrefix  缓存前缀
   * @param id         缓存后缀数据id
   * @param type       待查询数据类对象
   * @param dbCallback 数据库查询回调
   * @param time       缓存过期时间
   * @param unit       缓存过期时间单位
   * @param <ID>       缓存后缀类型
   * @param <R>        待查询数据类型
   * @return 缓存数据
   */
  public <ID, R> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                        Long time, TimeUnit unit,
                                        Function<ID, R> dbCallback) {
    String key = keyPrefix + id;
    String json = redisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(json)) {
      return JSONUtil.toBean(json, type);
    }
    if (json != null) {
      return null;
    }
    R r = dbCallback.apply(id);
    if (r == null) {
      redisTemplate.opsForValue().set(key, RedisConstant.CACHE_NULL_VALUE, RedisConstant.CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    this.set(key, r, time, unit);
    return r;
  }

  /**
   * 查询数据并利用分布式锁解决缓存击穿
   *
   * @param keyPrefix  缓存前缀
   * @param id         缓存后缀数据id
   * @param type       待查询数据类对象
   * @param dbCallback 数据库查询回调
   * @param time       缓存过期时间
   * @param unit       缓存过期时间单位
   * @param <ID>       缓存后缀类型
   * @param <R>        待查询数据类型
   * @return 缓存数据
   */
  public <ID, R> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
                                  Long time, TimeUnit unit,
                                  String lockKeyPrefix, Function<ID, R> dbCallback) {
    String key = keyPrefix + id;
    String lockKey = lockKeyPrefix + id;
    while (true) {
      String json = redisTemplate.opsForValue().get(key);
      // 缓存命中
      if (StrUtil.isNotBlank(json)) {
        return JSONUtil.toBean(json, type);
      }
      if (json != null) {
        return null;
      }
      // 缓存未命中，尝试获取分布式锁
      boolean locked = false;
      try {
        locked = tryAcquireDistributedLock(lockKey);
        if (!locked) {
          Thread.sleep(50);
          continue;
        }
        // double check
        json = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
          return JSONUtil.toBean(json, type);
        }
        if (json != null) {
          return null;
        }
        R r = dbCallback.apply(id);
        // 模拟重建延时
//        Thread.sleep(100);
        if (r == null) {
          redisTemplate.opsForValue().set(key, RedisConstant.CACHE_NULL_VALUE, RedisConstant.CACHE_NULL_TTL, TimeUnit.MINUTES);
          return null;
        }
        this.set(key, r, time, unit);
        return r;
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } finally {
        if (locked) {
          releaseDistributedLock(lockKey);
        }
      }
    }
  }

  /**
   * 查询数据并利用逻辑过期解决缓存击穿
   *
   * @param keyPrefix  缓存前缀
   * @param id         缓存后缀数据id
   * @param type       待查询数据类对象
   * @param dbCallback 数据库查询回调
   * @param time       缓存过期时间
   * @param unit       缓存过期时间单位
   * @param <ID>       缓存后缀类型
   * @param <R>        待查询数据类型
   * @return 缓存数据
   */
  public <ID, R> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type,
                                        Long time, TimeUnit unit,
                                        String lockKeyPrefix, Function<ID, R> dbCallback) {
    String key = keyPrefix + id;
    String json = redisTemplate.opsForValue().get(key);
    // 缓存命中空值
    if (StrUtil.isBlank(json)) {
      return null;
    }
    // 缓存命中非空值
    RedisData redisData = JSONUtil.toBean(json, RedisData.class);
    R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
    // 未过期，直接返回
    if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
      return r;
    }
    String lockKey = lockKeyPrefix + id;
    boolean locked = tryAcquireDistributedLock(lockKey);
    if (locked) {
      // double check
      json = redisTemplate.opsForValue().get(key);
      redisData = JSONUtil.toBean(json, RedisData.class);
      r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
      // 未过期，直接返回
      if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
        return r;
      }

//      log.debug("缓存过期，开始重建缓存");

      CACHE_REBUILD_EXECUTOR.submit(() -> {
        try {
          // 模拟重建延时
//          Thread.sleep(100);
          R result = dbCallback.apply(id);
          if (result == null) {
            redisTemplate.opsForValue().set(key, RedisConstant.CACHE_NULL_VALUE, RedisConstant.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return;
          }
          this.setWithLogicExpire(key, result, time, unit);
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          releaseDistributedLock(lockKey);
        }
      });
    }
    return r;
  }

  private <R> void setWithLogicExpire(String key, R result, Long time, TimeUnit unit) {
    LocalDateTime expireTime = LocalDateTime.now().plusSeconds(unit.toSeconds(time));
    RedisData data = RedisData.builder().expireTime(expireTime).data(result).build();
    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
  }

  private boolean tryAcquireDistributedLock(String key) {
    Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(success);
  }

  private void releaseDistributedLock(String key) {
    redisTemplate.opsForValue().getOperations().delete(key);
  }
}
