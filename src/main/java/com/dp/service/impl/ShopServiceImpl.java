package com.dp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
import com.dp.entity.Shop;
import com.dp.mapper.ShopMapper;
import com.dp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
  private final StringRedisTemplate redisTemplate;

  public ShopServiceImpl(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 根据id查询商铺信息
   *
   * @param id 商铺id
   * @return 商铺详情数据
   */
  @Override
  public Result queryById(Long id) {
    // 从redis查询缓存信息
    String key = RedisConstant.CACHE_SHOP_KEY + id;
    String shopJson = redisTemplate.opsForValue().get(key);
    // 如果缓存信息存在，直接返回
    if (StrUtil.isNotBlank(shopJson)) {
      return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
    }
    // 不存在，查数据库
    Shop shop = getById(id);
    if (shop == null) {
      return Result.fail(MessageConstant.SHOP_NOT_EXIST);
    }
    // 存在，写入缓存
    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
    return Result.ok(shop);
  }
}
