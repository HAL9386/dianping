package com.dp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
import com.dp.entity.Shop;
import com.dp.mapper.ShopMapper;
import com.dp.service.IShopService;
import com.dp.utils.CacheUtil;
import com.dp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
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
//    long start = System.currentTimeMillis();
    // 缓存穿透
//    Shop shop = cacheUtil.queryWithPassThrough(RedisConstant.CACHE_SHOP_KEY, id, Shop.class,
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
//    long end = System.currentTimeMillis();
//    System.out.println(end - start);
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

  /**
   * 根据商铺类型分页查询商铺信息
   * 如果提供了用户地址经纬度还可以根据距离排序返回结果
   *
   * @param typeId  商铺类型
   * @param current 页码
   * @param longitude 用户地址经度
   * @param latitude  用户地址纬度
   * @return 商铺列表
   */
  @Override
  public Result queryShopByType(Integer typeId, Integer current, Double longitude, Double latitude) {
    if (longitude == null || latitude == null) {
      // 根据类型分页查询
      Page<Shop> page = query()
        .eq("type_id", typeId)
        .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
      // 返回数据
      return Result.ok(page.getRecords());
    }
    // 计算分页参数
    int skipNum = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
    int endAt   = current       * SystemConstants.MAX_PAGE_SIZE;
    String key = RedisConstant.GEO_SHOPTYPE_KEY_PREFIX + typeId;
    // geosearch key BYLONLAT lng lat BYRADIUS 5 km WITHDISTANCE
    // 以用户地址为圆心查询附件店铺
    GeoResults<RedisGeoCommands.GeoLocation<String>> searchResult = redisTemplate.opsForGeo().search(
      key,
      GeoReference.fromCoordinate(longitude, latitude),
      new Distance(SystemConstants.GEO_SHOP_DISTANCE),
      RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(endAt)
    );
    if (searchResult == null) {
      return Result.ok(Collections.emptyList());
    }
    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoList = searchResult.getContent();
    if (geoList.size() <= skipNum) {
      // 没有更多数据
      return Result.ok(Collections.emptyList());
    }
    List<Long> shopIds = new ArrayList<>(geoList.size());
    // 绑定店铺id和距离
    Map<Long, Double> distanceMap = new HashMap<>(geoList.size());
    // 提取出店铺id并绑定距离
    geoList.stream().skip(skipNum).forEach(geoResult -> {
      Long shopId = Long.valueOf(geoResult.getContent().getName());
      shopIds.add(shopId);
      distanceMap.put(shopId, geoResult.getDistance().getValue());
    });
    // 根据店铺id列表查询店铺信息
    List<Shop> shopList = query().in("id", shopIds)
      .last("order by field(id," + StrUtil.join(",", shopIds) + ")").list();
    // 绑定距离
    shopList.forEach(shop -> shop.setDistance(distanceMap.get(shop.getId())));
    return Result.ok(shopList);
  }
}
