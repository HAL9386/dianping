package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
import com.dp.entity.VoucherOrder;
import com.dp.mapper.VoucherOrderMapper;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IVoucherOrderService;
import com.dp.utils.RedisIdWorker;
import com.dp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

  private final ISeckillVoucherService seckillVoucherService;
  private final RedisIdWorker redisIdWorker;
  private final StringRedisTemplate redisTemplate;
  private final IVoucherOrderService proxyService;
  private static final DefaultRedisScript<Long> SECKILL_VALIDATION_SCRIPT = new DefaultRedisScript<>();
  private static final ExecutorService ORDER_PERSIST_EXECUTOR = Executors.newSingleThreadExecutor();

  static {
    SECKILL_VALIDATION_SCRIPT.setLocation(new ClassPathResource("seckillValidation.lua"));
    SECKILL_VALIDATION_SCRIPT.setResultType(Long.class);
  }

  public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService,
                                 RedisIdWorker redisIdWorker,
                                 StringRedisTemplate redisTemplate,
                                 @Lazy IVoucherOrderService proxyService
  ) {
    this.seckillVoucherService = seckillVoucherService;
    this.redisIdWorker = redisIdWorker;
    this.redisTemplate = redisTemplate;
    this.proxyService = proxyService;
  }

  @PostConstruct
  private void init() {
    ORDER_PERSIST_EXECUTOR.submit(new OrderPersistHandler(redisTemplate, proxyService));
  }

  @PreDestroy
  private void shutdown() {
    ORDER_PERSIST_EXECUTOR.shutdown();
  }

  @Override
  public Result seckillVoucher(Long voucherId) {
    Long userId = UserHolder.getUser().getId();
    long orderId = redisIdWorker.nextId("order");
    long result = redisTemplate.execute(SECKILL_VALIDATION_SCRIPT,
      Collections.emptyList(),
      voucherId.toString(), userId.toString(), Long.toString(orderId));
    if (result == 1) {
      return Result.fail(MessageConstant.VOUCHER_STOCK_NOT_ENOUGH);
    }
    if (result == 2) {
      return Result.fail(MessageConstant.VOUCHER_ORDER_EXIST);
    }
    assert result == 0;
    return Result.ok(orderId);
  }

  private record OrderPersistHandler(
    StringRedisTemplate redisTemplate,
    IVoucherOrderService proxyService
  ) implements Runnable {
    @SuppressWarnings({"unchecked", "InfiniteLoopStatement"})
    @Override
    public void run() {
      try {
        redisTemplate.opsForStream().createGroup(RedisConstant.STREAM_ORDER, "group1");
      } catch (Exception e) {
        log.warn("stream订单消费组已存在", e);
      }
      while (true) {
        try {
          // XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS streams.orders >
          List<MapRecord<String, Object, Object>> orderList = redisTemplate.opsForStream().read(
            Consumer.from("group1", "consumer1"),
            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
            StreamOffset.create(RedisConstant.STREAM_ORDER, ReadOffset.lastConsumed())
          );
          if (orderList == null || orderList.isEmpty()) {
            continue;
          }
          // 解析订单信息
          MapRecord<String, Object, Object> record = orderList.get(0);
          VoucherOrder voucherOrder = unmarshalOrder(record);
          // 保存订单
          proxyService.persistOrder(voucherOrder);
          // 确认消息
          // SACK stream.orders g1 id
          redisTemplate.opsForStream().acknowledge(RedisConstant.STREAM_ORDER, "group1", record.getId());
        } catch (Exception e) {
          log.error("持久化订单错误", e);
          handlePendingList();
        }
      }
    }

    @SuppressWarnings("unchecked")
    private void handlePendingList() {
      while (true) {
        try {
          // XREADGROUP GROUP g1 c1 COUNT 1 STREAMS streams.orders 0
          List<MapRecord<String, Object, Object>> orderList = redisTemplate.opsForStream().read(
            Consumer.from("group1", "consumer1"),
            StreamReadOptions.empty().count(1),
            StreamOffset.create(RedisConstant.STREAM_ORDER, ReadOffset.from("0"))
          );
          if (orderList == null || orderList.isEmpty()) {
            break;
          }
          // 解析订单信息
          MapRecord<String, Object, Object> record = orderList.get(0);
          VoucherOrder voucherOrder = unmarshalOrder(record);
          // 保存订单
          proxyService.persistOrder(voucherOrder);
          // 确认消息
          // SACK stream.orders g1 id
          redisTemplate.opsForStream().acknowledge(RedisConstant.STREAM_ORDER, "group1", record.getId());
        } catch (Exception e) {
          log.error("持久化pendingList订单错误", e);
        }
      }
    }

    private VoucherOrder unmarshalOrder(MapRecord<String, Object, Object> record) {
      Map<Object, Object> orderMap = record.getValue();
      return BeanUtil.fillBeanWithMap(orderMap, new VoucherOrder(), true);
    }
  }

  @Transactional(rollbackFor = Exception.class)
  @Override
  public void persistOrder(VoucherOrder voucherOrder) {
    seckillVoucherService.update()
      .setSql("stock = stock - 1")
      .eq("voucher_id", voucherOrder.getVoucherId())
      .gt("stock", 0)
      .update();
    save(voucherOrder);
  }
}
