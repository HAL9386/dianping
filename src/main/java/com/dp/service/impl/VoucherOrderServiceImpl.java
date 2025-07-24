package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.entity.VoucherOrder;
import com.dp.mapper.VoucherOrderMapper;
import com.dp.service.IVoucherOrderService;
import org.springframework.stereotype.Service;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

}
