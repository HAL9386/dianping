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
}
