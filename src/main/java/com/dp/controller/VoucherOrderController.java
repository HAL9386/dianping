package com.dp.controller;


import com.dp.dto.Result;
import com.dp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

  private final IVoucherOrderService voucherOrderService;

  public VoucherOrderController(IVoucherOrderService voucherOrderService) {
    this.voucherOrderService = voucherOrderService;
  }

  /**
   * 秒杀优惠券
   *
   * @param voucherId 优惠券id
   * @return 结果
   */
  @PostMapping("seckill/{id}")
  public Result seckillVoucher(@PathVariable("id") Long voucherId) {
    return voucherOrderService.seckillVoucher(voucherId);
  }
}
