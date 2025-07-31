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
import com.dp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

  private final ISeckillVoucherService seckillVoucherService;
  private final RedisIdWorker redisIdWorker;

  public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService, RedisIdWorker redisIdWorker) {
    this.seckillVoucherService = seckillVoucherService;
    this.redisIdWorker = redisIdWorker;
  }

  @Transactional(rollbackFor = Exception.class)
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
