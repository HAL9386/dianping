package com.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.dto.Result;
import com.dp.entity.Blog;

public interface IBlogService extends IService<Blog> {

  Result queryHotBlog(Integer current);

  Result queryBlogById(Long id);

  Result likeBlog(Long id);

  Result queryBlogLikes(Long id);

  Result queryBlogByUserId(Integer current, Long id);

  Result saveBlog(Blog blog);

  /**
   * 滚动分页查询。我关注的用户的推送博客，从redis中的收件箱(zset)中查询
   * 例如，分数为8、8、8的记录，第一次读了3条，返回上次读取分数为8。下次读取时，利用8作为最大值查询，偏移为1的话，就会从第二个8开始读，错误！
   * 所以要设定偏移掉上次读到的相同分数值记录。偏移掉后面两个8。**设定偏移值为上次查询中与最后一个元素相同的元素个数（要包括最后那一个）**，这里为3。8、8、7的话偏移1。
   *
   * @param maxTime 上一次的查询的最小值作为本次查询的最大时间。如果是第一次查询，maxTime为当前时间。
   * @param offset 偏移量，第一次是0；后面的查询，是上一次查询中与最小值相同的元素个数(包括自己)。
   * @return 博客列表
   */
  Result queryBlogOfFollow(Long maxTime, Integer offset);
}
