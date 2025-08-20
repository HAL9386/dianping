package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.dto.Result;
import com.dp.dto.ScrollResult;
import com.dp.dto.UserDTO;
import com.dp.entity.Blog;
import com.dp.entity.Follow;
import com.dp.entity.User;
import com.dp.mapper.BlogMapper;
import com.dp.service.IBlogService;
import com.dp.service.IFollowService;
import com.dp.service.IUserService;
import com.dp.utils.SystemConstants;
import com.dp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
  private final IUserService userService;
  private final StringRedisTemplate redisTemplate;
  private final RedissonClient redissonClient;
  private final IFollowService followService;

  public BlogServiceImpl(IUserService userService, StringRedisTemplate redisTemplate, RedissonClient redissonClient, IFollowService followService) {
    this.userService = userService;
    this.redisTemplate = redisTemplate;
    this.redissonClient = redissonClient;
    this.followService = followService;
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

  @Override
  public Result queryBlogByUserId(Integer current, Long id) {
    Page<Blog> page = query().eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    List<Blog> records = page.getRecords();
    records.forEach(blog -> {
      queryBlogUser(blog);
      setBlogLiked(blog);
    });
    return Result.ok(records);
  }

  @Override
  public Result saveBlog(Blog blog) {
    // 获取登录用户
    UserDTO user = UserHolder.getUser();
    blog.setUserId(user.getId());
    // 保存探店博文
    save(blog);
    List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
    for (Follow follow : follows) {
      Long followeeUserId = follow.getUserId();
      String key = RedisConstant.FEED_KEY_PREFIX + followeeUserId;
      // 推送到关注着的收件箱（zset）
      redisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
    }
    // 返回id
    return Result.ok(blog.getId());
  }

  /**
   * 滚动分页查询。我关注的用户的推送博客，从redis中的收件箱(zset)中查询。
   * 因为要分页查询，如果用页号和每页数量，会有问题。在查后面的页时，如果有新的记录插入，会重复查询。
   * 例如，9、8、7，第一次查9一页一个，然后新插入了10，第二次查页号2，一页一个，
   * 例如，分数为8、8、8的记录，第一次读了3条，返回上次读取分数为8。下次读取时，利用8作为最大值查询，偏移为1的话，就会从第二个8开始读，错误！
   * 所以要设定偏移掉上次读到的相同分数值记录。偏移掉后面两个8。**设定偏移值为上次查询中与最后一个元素相同的元素个数（要包括最后那一个）**，这里为3。8、8、7的话偏移1。
   *
   * @param maxTime 上一次的查询的最小值作为本次查询的最大时间。如果是第一次查询，maxTime为当前时间。
   * @param offset  偏移量，第一次是0；后面的查询，是上一次查询中与最小值相同的元素个数(包括自己)。
   * @return 博客列表
   */
  @Override
  public Result queryBlogOfFollow(Long maxTime, Integer offset) {
    // 1. 获取当前登录用户
    Long userId = UserHolder.getUser().getId();
    // 2. 查询收件箱
    String key = RedisConstant.FEED_KEY_PREFIX + userId;
//    zrevrangebyscore key max min [withscores] [LIMIT offset count]
// max: 分数最大值，min: 分数最小值
// offset: 小于等于最大值的偏移处开始查
// count: 查几条
// 当分数一样时，从最靠前的开始
// (m7, 6) (m6, 6) (m5, 5) (m4, 4) (m3, 3) (m2, 2) (m1, 1)
//    zrevrangebyscore z 6 0 withscores LIMIT 1 1
//-> (m6, 6)
    Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, maxTime, offset, 2);
    if (tuples == null || tuples.isEmpty()) {
      return Result.ok(Collections.emptyList());
    }
    // 3. 解析数据计算下一次的maxTime和偏移量
    List<Long> blogIds = new ArrayList<>(tuples.size());
    long minTime = 0L;
    int minCount = 0;
    for (ZSetOperations.TypedTuple<String> tuple : tuples) {
      String value = tuple.getValue();
      Double score = tuple.getScore();
      if (value == null || score == null) {
        // 理论不应发生，出现则说明底层返回了异常数据，跳过
        // Redis 有序集合协议虽然不允许 null 成员与 null 分数，但框架为了通用性保留了可空签名。
        // 因此 IDE 给出“可能为 null”的警告；这并非表示此处一定会出现 null，而是类型系统保守提示。
        continue;
      }
      blogIds.add(Long.valueOf(value));
      long time = score.longValue();
      if (time == minTime) {
        minCount++;
      } else {
        minTime = time;
        minCount = 1;
      }
    }
    // 如果这次查询的最小值与上次查询的最小值相同，说明这一时间戳的记录横跨多页。
    // 这一次的偏移要加上上次查询的偏移量。
    // 例如，9 8, 8 8 第一次查询9 8 最小值8，偏移1；第二次查询8 8 最小值8，偏移2；
    // 下一次查询时，最小值参数为8，偏移应该要是1+2=3
    offset = minTime == maxTime ? offset + minCount : minCount;
    // 4. 根据收件箱中的blogId查询blog
    List<Blog> blogs = query().in("id", blogIds)
      .last("order by field(id," + StrUtil.join(",", blogIds) + ")")
      .list();
    blogs.forEach(blog -> {
      queryBlogUser(blog);
      setBlogLiked(blog);
    });
    // 5. 封装滚动分页结果
    ScrollResult<Blog> scrollResult = ScrollResult.<Blog>builder()
      .list(blogs)
      .minTime(minTime)
      .offset(offset)
      .build();
    return Result.ok(scrollResult);
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
