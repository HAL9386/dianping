package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.dto.Result;
import com.dp.entity.SeckillVoucher;
import com.dp.entity.VoucherOrder;
import com.dp.mapper.VoucherOrderMapper;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IVoucherOrderService;
import com.dp.utils.RedisIdWorker;
import com.dp.utils.SimpleRedisLock;
import com.dp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

  private final ISeckillVoucherService seckillVoucherService;
  private final RedisIdWorker redisIdWorker;
  private final StringRedisTemplate redisTemplate;

  public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService, RedisIdWorker redisIdWorker, StringRedisTemplate redisTemplate) {
    this.seckillVoucherService = seckillVoucherService;
    this.redisIdWorker = redisIdWorker;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Result seckillVoucher(Long voucherId) {
    // 1.查询优惠券
    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    if (voucher == null) {
      return Result.fail(MessageConstant.VOUCHER_NOT_EXIST);
    }
    // 2.优惠券是否在有效期内
    LocalDateTime now = LocalDateTime.now();
    if (now.isBefore(voucher.getBeginTime())) {
      return Result.fail(MessageConstant.VOUCHER_NOT_BEGIN);
    }
    if (now.isAfter(voucher.getEndTime())) {
      return Result.fail(MessageConstant.VOUCHER_ALREADY_END);
    }
    // 3.优惠券库存是否充足
    int stock = voucher.getStock();
    if (stock < 1) {
      return Result.fail(MessageConstant.VOUCHER_STOCK_NOT_ENOUGH);
    }

    // 如果锁Long对象或者String对象，即使同一个id值，也会创建新的对象。
    // 所以对于用一个用户的多次请求，它们不是互斥的。
    // 可以将id转为String，然后调用intern()方法，将字符串放入字符串常量池。
    // 这样，对于同一个用户的多次请求，它们会使用同一个字符串对象，从而实现互斥。
    Long userId = UserHolder.getUser().getId();
    SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, redisTemplate);
    if (!lock.tryLock(5)) {
      return Result.fail(MessageConstant.VOUCHER_ORDER_EXIST);
    }
    try {
      IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
      return proxy.createVoucherOrder(voucherId, userId);
    } finally {
      lock.unlock();
    }
  }

  @Transactional(rollbackFor = Exception.class)
  public Result createVoucherOrder(Long voucherId, Long userId) {
    long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    if (count > 0) {
      return Result.fail(MessageConstant.VOUCHER_ORDER_EXIST);
    }
    // 4.扣减库存
    boolean success = seckillVoucherService.update()
      .setSql("stock = stock - 1")
      .eq("voucher_id", voucherId)
      .gt("stock", 0) // 乐观锁解决超卖问题，库存可以作为版本号
      .update();
    if (!success) {
      return Result.fail(MessageConstant.VOUCHER_STOCK_NOT_ENOUGH);
    }
    // 5.创建订单
    VoucherOrder voucherOrder = VoucherOrder.builder()
      .id(redisIdWorker.nextId("order"))
      .userId(UserHolder.getUser().getId())
      .voucherId(voucherId)
      .build();
    save(voucherOrder);
    return Result.ok(voucherOrder.getId());
  }
}
