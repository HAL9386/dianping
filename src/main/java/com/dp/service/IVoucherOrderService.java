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
}
