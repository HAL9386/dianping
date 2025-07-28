package com.dp.controller;


import cn.hutool.json.JSONUtil;
import com.dp.constant.RedisConstant;
import com.dp.constant.ShopTypeEntityConstant;
import com.dp.dto.Result;
import com.dp.entity.ShopType;
import com.dp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
  private final IShopTypeService typeService;
  private final StringRedisTemplate redisTemplate;

  public ShopTypeController(IShopTypeService typeService, StringRedisTemplate redisTemplate) {
    this.typeService = typeService;
    this.redisTemplate = redisTemplate;
  }

  /**
   * 查询所有商铺类型并缓存字符串类型
   *
   * @return 商铺类型列表
   */
  @GetMapping("list")
  public Result queryTypeList() {
    // 从缓存中查询
    String key = RedisConstant.CACHE_SHOP_TYPE_KEY;
    String typeListJson = redisTemplate.opsForValue().get(key);
    if (typeListJson != null) {
      return Result.ok(JSONUtil.toList(typeListJson, ShopType.class));
    }
    // 缓存不存在，查询数据库
    List<ShopType> typeList = typeService
      .query().orderByAsc(ShopTypeEntityConstant.COLUMN_SORT).list();
    // 写入缓存
    redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
    return Result.ok(typeList);
  }

  /**
   * 查询所有商铺类型并缓存列表类型
   *
   * @return 商铺类型列表
   */
//  @GetMapping("/list")
  public Result queryTypeListAll() {
    String key = RedisConstant.CACHE_SHOP_TYPE_KEY;
    // 从缓存中查询shopType列表
    List<String> typeList = redisTemplate.opsForList().range(key, 0, -1);
    if (typeList != null && !typeList.isEmpty()) {
      return Result.ok(typeList.stream()
        .map(typeJson -> JSONUtil.toBean(typeJson, ShopType.class))
        .toList());
    }
    List<ShopType> shopTypeList = typeService.query().orderByAsc(ShopTypeEntityConstant.COLUMN_SORT).list();
    // 写入缓存
    redisTemplate.opsForList().rightPushAll(key, shopTypeList.stream().map(JSONUtil::toJsonStr).toList());
    return Result.ok(shopTypeList);
  }
}
