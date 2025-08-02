package com.dp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock {
  private final String name;
  private final StringRedisTemplate redisTemplate;
  private static final String LOCK_KEY_PREFIX = "lock:";
  // 一个服务器共用一个线程ID前缀
//  如果A线程执行业务耗时久，锁过期释放。
//  B线程就可以获取锁执行业务，随后A线程又释放锁。
//  其他线程就可以抢锁执行，并没有保证操作的原子性以及操作的互斥性。
//  加锁时存入一个唯一值，使用uuid，并在工具类内部维护。单一职责不影响
//  外部业务的代码，否则还要在外部生成uuid再传入工具类加锁，
//  并保存到线程。释放时还要释放该资源。
//  这样，一个服务器内部调用工具类获取到的都是同一个uuid，再拼上线程id，
//  做到唯一。
  private static final String THREAD_ID_PREFIX = UUID.randomUUID().toString(true) + "-";
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

  static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);
  }

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
//    String threadId = THREAD_ID_PREFIX + Thread.currentThread().getId();
//    String lockKey = LOCK_KEY_PREFIX + this.name;
//    if (threadId.equals(redisTemplate.opsForValue().get(lockKey))) {
//      redisTemplate.delete(lockKey);
//    }

    redisTemplate.execute(
      UNLOCK_SCRIPT,
      List.of(LOCK_KEY_PREFIX + name),
      THREAD_ID_PREFIX + Thread.currentThread().getId()
    );
  }
}
