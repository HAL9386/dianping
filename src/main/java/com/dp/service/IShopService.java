package com.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.dto.Result;
import com.dp.entity.Shop;

public interface IShopService extends IService<Shop> {

  /**
   * 根据id查询商铺信息
   *
   * @param id 商铺id
   * @return 商铺详情数据
   */
  Result queryById(Long id);

  /**
   * 更新商铺信息
   *
   * @param shop 商铺数据
   * @return 无
   */
  Result update(Shop shop);
}
