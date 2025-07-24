package com.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.dto.Result;
import com.dp.entity.Voucher;

public interface IVoucherService extends IService<Voucher> {

  Result queryVoucherOfShop(Long shopId);

  void addSeckillVoucher(Voucher voucher);
}
