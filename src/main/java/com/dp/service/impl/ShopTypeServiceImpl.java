package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.entity.ShopType;
import com.dp.mapper.ShopTypeMapper;
import com.dp.service.IShopTypeService;
import org.springframework.stereotype.Service;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

}
