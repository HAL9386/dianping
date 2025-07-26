package com.dp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * 关联的优惠券的id
   */
  @TableId(value = "voucher_id", type = IdType.INPUT)
  private Long voucherId;

  /**
   * 库存
   */
  private Integer stock;

  /**
   * 创建时间
   */
  private LocalDateTime createTime;

  /**
   * 生效时间
   */
  private LocalDateTime beginTime;

  /**
   * 失效时间
   */
  private LocalDateTime endTime;

  /**
   * 更新时间
   */
  private LocalDateTime updateTime;


}
