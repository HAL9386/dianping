package com.dp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.dp.constant.RedisConstant;
import com.dp.entity.SeckillVoucher;
import com.dp.entity.Shop;
import com.dp.entity.User;
import com.dp.entity.VoucherOrder;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IShopService;
import com.dp.service.IVoucherOrderService;
import com.dp.utils.RedisData;
import com.dp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class DianPingApplicationTests {
  @Autowired
  private IVoucherOrderService voucherOrderService;

  @Autowired
  private ISeckillVoucherService seckillVoucherService;

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

  // 加载店铺的地理位置数据到redis里的geo
  // 键为 geo:shopType:{shopType}
  @Test
  public void loadShopGeoDataUsingGroup() {
    // 1. 查询所有店铺信息
    List<Shop> shopList = shopService.list();
    // 2. 将店铺信息按类型分组
    Map<Long, List<Shop>> shopMap = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
    // 3. 添加到redis的geo里
    for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
      Long shopTypeId = entry.getKey();
      List<Shop> shops = entry.getValue();
      List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
      for (Shop shop : shops) {
//        redisTemplate.opsForGeo().add(
//          RedisConstant.GEO_SHOPTYPE_KEY_PREFIX + shopTypeId,
//          new Point(shop.getX(), shop.getY()),
//          shop.getId().toString()
//        );
        locations.add(new RedisGeoCommands.GeoLocation<>(
            shop.getId().toString(),
            new Point(shop.getX(), shop.getY())
          )
        );
      }
      redisTemplate.opsForGeo().add(RedisConstant.GEO_SHOPTYPE_KEY_PREFIX + shopTypeId, locations);
    }
  }

  @Test
  public void loadShopGeoDataUsingLoop() {
    // 1. 查询所有店铺信息
    List<Shop> shopList = shopService.list();
    // 2. 添加到redis的geo里
    for (Shop shop : shopList) {
      redisTemplate.opsForGeo().add(
        RedisConstant.GEO_SHOPTYPE_KEY_PREFIX + shop.getTypeId(),
        new Point(shop.getX(), shop.getY()),
        shop.getId().toString()
      );
    }
  }

  @Test
  public void rebuildSeckillInRedis() {
    // 1. 查询所有秒杀商品
    List<SeckillVoucher> seckillVoucherList = seckillVoucherService.list();
    // 2. 重建秒杀商品的库存
    for (SeckillVoucher seckillVoucher : seckillVoucherList) {
      redisTemplate.opsForValue().set(RedisConstant.SECKILL_STOCK_KEY + seckillVoucher.getVoucherId(), seckillVoucher.getStock().toString());
    }
    // 3. 重建秒杀商品的已购订单用户集合
    List<VoucherOrder> orderList = voucherOrderService.list();
    Map<Long, List<VoucherOrder>> orderMap = orderList.stream().collect(Collectors.groupingBy(VoucherOrder::getVoucherId));
    for (Map.Entry<Long, List<VoucherOrder>> entry : orderMap.entrySet()) {
      Long voucherId = entry.getKey();
      List<VoucherOrder> orders = entry.getValue();
      String key = RedisConstant.SECKILL_ORDER_KEY + voucherId;
      // 将用户id转为String数组，作为可变参数传入
      String[] userIds = orders.stream()
        .map(VoucherOrder::getUserId)
        .map(String::valueOf)
        .toArray(String[]::new);
      redisTemplate.opsForSet().add(key, userIds);
    }
  }
}
