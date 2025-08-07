package com.dp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.Blog;
import com.dp.service.IBlogService;
import com.dp.utils.SystemConstants;
import com.dp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog")
public class BlogController {

  private final IBlogService blogService;

  public BlogController(IBlogService blogService) {
    this.blogService = blogService;
  }

  // 发布探店博文
  @PostMapping
  public Result saveBlog(@RequestBody Blog blog) {
    // 获取登录用户
    UserDTO user = UserHolder.getUser();
    blog.setUserId(user.getId());
    // 保存探店博文
    blogService.save(blog);
    // 返回id
    return Result.ok(blog.getId());
  }

  // 点赞笔记
  @PutMapping("/like/{id}")
  public Result likeBlog(@PathVariable("id") Long id) {
    return blogService.likeBlog(id);
  }

  // 查询笔记点赞用户
  @GetMapping("/likes/{id}")
  public Result queryBlogLikes(@PathVariable("id") Long id) {
    return blogService.queryBlogLikes(id);
  }

  @GetMapping("/of/me")
  public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
    // 获取登录用户
    UserDTO user = UserHolder.getUser();
    // 根据用户查询
    Page<Blog> page = blogService.query()
      .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();
    return Result.ok(records);
  }

  @GetMapping("/hot")
  public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
    return blogService.queryHotBlog(current);
  }

  @GetMapping("/{id}")
  public Result queryBlogById(@PathVariable("id") Long id) {
    return blogService.queryBlogById(id);
  }

  @GetMapping("/of/user")
  public Result queryBlogByUserId(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                  @RequestParam("id") Long id) {
    return blogService.queryBlogByUserId(current, id);
  }
}
