package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.Follow;
import com.dp.mapper.FollowMapper;
import com.dp.service.IFollowService;
import com.dp.service.IUserService;
import com.dp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

  private final IUserService userService;
  public FollowServiceImpl(IUserService userService) {
    this.userService = userService;
  }

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

  /**
   * 获取共同关注
   *
   * @param checkUserId 在用户主页查看的用户ID
   * @return 共同关注结果
   */
  @Override
  public Result followCommon(Long checkUserId) {
    Long userId = UserHolder.getUser().getId();
    List<Long> commonFollowUserIds = getBaseMapper().selectCommonFollowUserIds(userId, checkUserId);
    // 加载用户信息
    List<UserDTO> userDTOS = userService.listByIds(commonFollowUserIds).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
    return Result.ok(userDTOS);
  }
}
