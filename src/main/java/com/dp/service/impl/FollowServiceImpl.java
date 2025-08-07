package com.dp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.Result;
import com.dp.entity.Follow;
import com.dp.mapper.FollowMapper;
import com.dp.service.IFollowService;
import com.dp.utils.UserHolder;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

  @Override
  public Result follow(Long followUserId, Boolean isFollow) {
    Long userId = UserHolder.getUser().getId();
    if (isFollow) {
      Follow follow = Follow.builder().userId(userId).followUserId(followUserId).build();
      save(follow);
    } else {
      remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
    }
    return Result.ok();
  }

  @Override
  public Result isFollow(Long followUserId) {
    Long userId = UserHolder.getUser().getId();
    Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
    return Result.ok(count > 0);
  }
}
