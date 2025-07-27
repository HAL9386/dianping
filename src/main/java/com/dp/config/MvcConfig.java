package com.dp.config;

import com.dp.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
  private final StringRedisTemplate stringRedisTemplate;

  public MvcConfig(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new LoginInterceptor(stringRedisTemplate)).excludePathPatterns(
      "/shop/**",
      "/voucher/**",
      "/shop-type/**",
      "/upload/**",
      "/blog/hot",
      "/user/code",
      "/user/login"
    ).order(1);
  }
}
