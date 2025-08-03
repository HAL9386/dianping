package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
import com.dp.entity.SeckillVoucher;
import com.dp.entity.Voucher;
import com.dp.mapper.VoucherMapper;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

  private final ISeckillVoucherService seckillVoucherService;
  private final StringRedisTemplate redisTemplate;

  public VoucherServiceImpl(ISeckillVoucherService seckillVoucherService, StringRedisTemplate redisTemplate) {
    this.seckillVoucherService = seckillVoucherService;
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Result queryVoucherOfShop(Long shopId) {
    // 查询优惠券信息
    List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
    // 返回结果
    return Result.ok(vouchers);
  }

  @Override
  @Transactional
  public void addSeckillVoucher(Voucher voucher) {
    // 保存优惠券
    save(voucher);
    // 保存秒杀信息
    SeckillVoucher seckillVoucher = new SeckillVoucher();
    seckillVoucher.setVoucherId(voucher.getId());
    seckillVoucher.setStock(voucher.getStock());
    seckillVoucher.setBeginTime(voucher.getBeginTime());
    seckillVoucher.setEndTime(voucher.getEndTime());
    seckillVoucherService.save(seckillVoucher);
    TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          redisTemplate.opsForValue().set(RedisConstant.SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
        }
      }
    );
  }
}
