package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.entity.Shop;
import com.dp.mapper.ShopMapper;
import com.dp.service.IShopService;
import org.springframework.stereotype.Service;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

}
