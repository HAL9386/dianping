package com.dp.utils;

public interface ILock {
  /**
   * 尝试获取锁
   *
   * @param timeoutSec 锁超时时间，单位秒。到期自动释放锁
   * @return true 成功 false 失败
   */
  boolean tryLock(long timeoutSec);

  /**
   * 释放锁
   */
  void unlock();
}
