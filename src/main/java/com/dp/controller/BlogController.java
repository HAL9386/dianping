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
    return blogService.saveBlog(blog);
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

  /**
   * 滚动分页查询。我关注的用户的推送博客，从redis中的收件箱(zset)中查询
   * 例如，分数为8、8、8的记录，第一次读了3条，返回读取分数为8。下次读取时，利用8作为最大值查询，偏移为1的话，就会从第二个8开始读，错误！
   * 所以要设定偏移掉上次读到的相同分数值记录。偏移掉后面两个8。**设定偏移值为上次查询中与最后一个元素相同的元素个数（要包括最后那一个）**，这里为3。8、8、7的话偏移1。
   *
   * @param maxTime 上一次的查询的最小值作为本次查询的最大时间。如果是第一次查询，maxTime为当前时间。
   * @param offset 偏移量，第一次是0；后面的查询，是上一次查询中与最小值相同的元素个数(包括自己)。
   * @return 博客列表
   */
  @GetMapping("/of/follow")
  public Result queryBlogOfFollow(@RequestParam("lastId") Long maxTime,
                                  @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
    return blogService.queryBlogOfFollow(maxTime, offset);
  }
}
