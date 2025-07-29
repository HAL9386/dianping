package com.dp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.dp.constant.RedisConstant;
import com.dp.entity.Shop;
import com.dp.entity.User;
import com.dp.service.IShopService;
import com.dp.utils.RedisData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@SpringBootTest
class DianPingApplicationTests {
  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  private IShopService shopService;

  @Test
  public void testUser() {
    User user = User.builder().phone("13800000000").password("123456").build();
    System.out.println(user);
  }

  @Test
  public void testBeanToMap() {
    User user = User.builder().phone("13800000000").password("123456").build();
    Map<String, Object> userMap = BeanUtil.beanToMap(user);
    System.out.println(userMap);
  }

  @Test
  public void warmUpRedis() {
    Shop shop = shopService.getById(1L);
    RedisData redisData = RedisData.builder().data(shop).expireTime(LocalDateTime.now().plusSeconds(30)).build();
    redisTemplate.opsForValue().set(RedisConstant.CACHE_SHOP_KEY + 1L, JSONUtil.toJsonStr(redisData));
  }

  @Test
  public void testGetCacheShop() {
    String json = redisTemplate.opsForValue().get(RedisConstant.CACHE_SHOP_KEY + 1L);
    RedisData redisData = JSONUtil.toBean(json, RedisData.class);
    System.out.println(redisData.getExpireTime().toString());
  }
}
