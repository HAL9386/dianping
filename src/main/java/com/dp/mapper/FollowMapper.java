package com.dp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dp.entity.Follow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {
  List<Long> selectCommonFollowUserIds(@Param("userId") Long userId,
                                       @Param("checkUserId") Long checkUserId);

}
