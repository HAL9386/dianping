package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

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
    try {
      if (lock.tryLock(10, TimeUnit.SECONDS)) {
        try {
          Long userId = UserHolder.getUser().getId();
          String key = RedisConstant.BLOG_LIKED_KEY_PREFIX + id;
          Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
          if (Boolean.TRUE.equals(isMember)) {
            // 如果已点赞，取消点赞
            if (update().setSql("liked = liked - 1").eq("id", id).update()) {
              redisTemplate.opsForSet().remove(key, userId.toString());
            }
          } else {
            // 如果未点赞，点赞
            if (update().setSql("liked = liked + 1").eq("id", id).update()) {
              redisTemplate.opsForSet().add(key, userId.toString());
            }
          }
        } finally {
          lock.unlock();
        }
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return Result.ok();
  }

private void setBlogLiked(Blog blog) {
  if (UserHolder.getUser() == null) {
    // 用户未登录，无需设置点赞状态
    return;
  }
  Long userId = UserHolder.getUser().getId();
  String key = RedisConstant.BLOG_LIKED_KEY_PREFIX + blog.getId();
  Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
  blog.setIsLike(Boolean.TRUE.equals(isMember));
}

private void queryBlogUser(Blog blog) {
  Long userId = blog.getUserId();
  User user = userService.getById(userId);
  blog.setName(user.getNickName());
  blog.setIcon(user.getIcon());
}
}
