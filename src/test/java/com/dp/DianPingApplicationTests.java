package com.dp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.dp.constant.RedisConstant;
import com.dp.entity.Shop;
import com.dp.entity.User;
import com.dp.service.IShopService;
import com.dp.utils.RedisData;
import com.dp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class DianPingApplicationTests {
  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  private IShopService shopService;

  @Autowired
  private RedisIdWorker redisIdWorker;

  private final ExecutorService es = Executors.newFixedThreadPool(500);

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

  @Test
  public void testRedisIdWorker() {
    CountDownLatch count = new CountDownLatch(300);
    Runnable task = () -> {
      for (int i = 0; i < 100; i++) {
        long id = redisIdWorker.nextId("order");
        System.out.println(id);
      }
      count.countDown();
    };
    long start = System.currentTimeMillis();
    for (int i = 0; i < 300; i++) {
      es.submit(task);
    }
    try {
      count.await();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    long end = System.currentTimeMillis();
    System.out.println("time: " + (end - start));
  }
}
