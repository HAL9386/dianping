package com.dp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedissonTests {
  @Autowired
  private RedissonClient redissonClient;

  private RLock lock;

  @BeforeEach
  public void init() {
    lock = redissonClient.getLock("lock");
  }

  @Test
  public void testLock() {
    try {
      if (!lock.tryLock(10, TimeUnit.SECONDS)) {
        System.out.println("lock fail");
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      lock.unlock();
    }
  }
}
