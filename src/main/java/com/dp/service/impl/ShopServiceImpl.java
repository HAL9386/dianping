package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
import com.dp.entity.Shop;
import com.dp.mapper.ShopMapper;
import com.dp.service.IShopService;
import com.dp.utils.CacheUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
  private final StringRedisTemplate redisTemplate;
  private final CacheUtil cacheUtil;

  public ShopServiceImpl(StringRedisTemplate redisTemplate, CacheUtil cacheUtil) {
    this.redisTemplate = redisTemplate;
    this.cacheUtil = cacheUtil;
  }

  /**
   * 根据id查询商铺信息
   *
   * @param id 商铺id
   * @return 商铺详情数据
   */
  @Override
  public Result queryById(Long id) {
    // 解决缓存穿透
//    Shop shop = cacheUtil.queryWithPassThrough(key, id, Shop.class,
//      RedisConstant.CACHE_SHOP_TTL, TimeUnit.MINUTES,
//      this::getById);
    // 利用互斥锁解决缓存击穿
    Shop shop = cacheUtil.queryWithMutex(RedisConstant.CACHE_SHOP_KEY, id, Shop.class,
      RedisConstant.CACHE_SHOP_TTL, TimeUnit.MINUTES,
      RedisConstant.LOCK_SHOP_KEY_PREFIX, this::getById);
    // 利用逻辑过期解决缓存击穿
//    Shop shop = cacheUtil.queryWithLogicExpire(RedisConstant.CACHE_SHOP_KEY, id, Shop.class,
//      RedisConstant.CACHE_SHOP_TTL, TimeUnit.MINUTES,
//      RedisConstant.LOCK_SHOP_KEY_PREFIX, this::getById);
    if (shop == null) {
      return Result.fail(MessageConstant.SHOP_NOT_EXIST);
    }
    return Result.ok(shop);
  }

  /**
   * 更新商铺信息
   *
   * @param shop 商铺数据
   * @return 无
   */
  @Transactional(rollbackFor = Exception.class)
  @Override
  public Result update(Shop shop) {
    if (shop.getId() == null) {
      return Result.fail(MessageConstant.SHOP_NOT_EXIST);
    }
    // 更新数据库
    updateById(shop);
    // 删除缓存
    // 如果删除缓存后发生异常，数据库回滚但redis未删除，会导致缓存与数据库不一致
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          redisTemplate.delete(RedisConstant.CACHE_SHOP_KEY + shop.getId());
        }
      }
    );
    return Result.ok();
  }
}
