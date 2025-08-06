package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.dto.Result;
import com.dp.entity.Blog;
import com.dp.entity.User;
import com.dp.mapper.BlogMapper;
import com.dp.service.IBlogService;
import com.dp.service.IUserService;
import com.dp.utils.SystemConstants;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
  private final IUserService userService;

  public BlogServiceImpl(IUserService userService) {
    this.userService = userService;
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
    records.forEach(this::queryBlogUser);
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
    return Result.ok(blog);
  }

  private void queryBlogUser(Blog blog) {
    Long userId = blog.getUserId();
    User user = userService.getById(userId);
    blog.setName(user.getNickName());
    blog.setIcon(user.getIcon());
  }
}
