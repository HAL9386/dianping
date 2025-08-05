package com.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.dto.Result;
import com.dp.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

  /**
   * 秒杀优惠券
   *
   * @param voucherId 优惠券id
   * @return 结果
   */
  Result seckillVoucher(Long voucherId);

  /**
   * 保存优惠券订单。下单时由异步线程调用执行。
   *
   * @param voucherOrder 优惠券订单
   */
  void persistOrder(VoucherOrder voucherOrder);
}
