package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.Blog;
import com.dp.entity.User;
import com.dp.mapper.BlogMapper;
import com.dp.service.IBlogService;
import com.dp.service.IUserService;
import com.dp.utils.SystemConstants;
import com.dp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
  private final IUserService userService;
  private final StringRedisTemplate redisTemplate;
  private final RedissonClient redissonClient;

  public BlogServiceImpl(IUserService userService, StringRedisTemplate redisTemplate, RedissonClient redissonClient) {
    this.userService = userService;
    this.redisTemplate = redisTemplate;
    this.redissonClient = redissonClient;
  }

  @Override
  public Result queryHotBlog(Integer current) {
    // 根据用户查询
    Page<Blog> page = query()
      .orderByDesc("liked")
      .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();
    // 查询用户
    records.forEach(blog -> {
      queryBlogUser(blog);
      setBlogLiked(blog);
    });
    return Result.ok(records);
  }

  @Override
  public Result queryBlogById(Long id) {
    Blog blog = getById(id);
    if (blog == null) {
      return Result.fail(MessageConstant.BLOG_NOT_EXIST);
    }
    // 查询用户
    queryBlogUser(blog);
    // 设置是否点赞
    setBlogLiked(blog);
    return Result.ok(blog);
  }

  @Override
  public Result likeBlog(Long id) {
    RLock lock = redissonClient.getLock(RedisConstant.LOCK_BLOG_LIKED_PREFIX + id);
    if (!lock.tryLock()) {
      return Result.ok();
    }
    try {
      Long userId = UserHolder.getUser().getId();
      String key = RedisConstant.BLOG_LIKED_KEY_PREFIX + id;
      Double score = redisTemplate.opsForZSet().score(key, userId.toString());
      if (score != null) {
        // 如果已点赞，取消点赞
        if (update().setSql("liked = liked - 1").eq("id", id).update()) {
          redisTemplate.opsForZSet().remove(key, userId.toString());
        }
      } else {
        // 如果未点赞，点赞
        if (update().setSql("liked = liked + 1").eq("id", id).update()) {
          redisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }
      }
    } finally {
      lock.unlock();
    }
    return Result.ok();
  }

  @Override
  public Result queryBlogLikes(Long id) {
    String key = RedisConstant.BLOG_LIKED_KEY_PREFIX + id;
    Set<String> top5UserIds = redisTemplate.opsForZSet().range(key, 0, 4);
    if (top5UserIds == null || top5UserIds.isEmpty()) {
      return Result.ok(Collections.emptyList());
    }
    List<Long> userIds = top5UserIds.stream().map(Long::valueOf).toList();
    String userIdsStr = StrUtil.join(",", userIds);
    // 指定mysql按照zset中得到的顺序返回
    List<UserDTO> userDTOList = userService.query()
      .in("id", userIds).last("order by field(id," + userIdsStr + ")").list()
      .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();
    return Result.ok(userDTOList);
  }

  private void setBlogLiked(Blog blog) {
    if (UserHolder.getUser() == null) {
      // 用户未登录，无需设置点赞状态
      return;
    }
    Long userId = UserHolder.getUser().getId();
    String key = RedisConstant.BLOG_LIKED_KEY_PREFIX + blog.getId();
    Double score = redisTemplate.opsForZSet().score(key, userId.toString());
    blog.setIsLike(score != null);
  }

  private void queryBlogUser(Blog blog) {
    Long userId = blog.getUserId();
    User user = userService.getById(userId);
    blog.setName(user.getNickName());
    blog.setIcon(user.getIcon());
  }
}
