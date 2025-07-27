package com.dp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.dp.constant.RedisConstant;
import com.dp.dto.UserDTO;
import com.dp.utils.SystemConstants;
import com.dp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
  private final StringRedisTemplate stringRedisTemplate;

  public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
    // 1 获取请求头中的token
    String token = request.getHeader(SystemConstants.HEADER_TOKEN_KEY);
    if (StrUtil.isBlank(token)) {
      return true;
    }
    // 2 根据token从redis hash中获取UserDTO数据
    Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstant.LOGIN_USER_KEY_PREFIX + token);
    if (userMap.isEmpty()) {
      return true;
    }
    // 3 将获取的数据转为UserDTO对象
    UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    UserHolder.saveUser(user);
    // 4 刷新token过期时间
    stringRedisTemplate.expire(RedisConstant.LOGIN_USER_KEY_PREFIX + token, RedisConstant.LOGIN_USER_TTL, TimeUnit.MINUTES);
    return true;
  }

  @Override
  public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
    UserHolder.removeUser();
  }
}
