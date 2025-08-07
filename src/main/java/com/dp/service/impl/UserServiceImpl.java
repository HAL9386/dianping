package com.dp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.constant.MessageConstant;
import com.dp.constant.RedisConstant;
import com.dp.constant.UserEntityConstant;
import com.dp.dto.LoginFormDTO;
import com.dp.dto.Result;
import com.dp.dto.UserDTO;
import com.dp.entity.User;
import com.dp.mapper.UserMapper;
import com.dp.service.IUserService;
import com.dp.utils.RegexUtils;
import com.dp.utils.SystemConstants;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
  private final StringRedisTemplate stringRedisTemplate;

  public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /**
   * 发送短信验证码
   *
   * @param phone   手机号
   * @param session session
   * @return 发送结果
   */
  @Override
  public Result sendCode(String phone, HttpSession session) {
    // 校验手机号
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail(MessageConstant.INVALID_PHONE_NUMBER);
    }
    // 生成验证码
    String code = RandomUtil.randomNumbers(SystemConstants.VERIFICATION_CODE_LENGTH);
    // 保存验证码到Redis
    stringRedisTemplate.opsForValue().set(RedisConstant.LOGIN_CODE_KEY_PREFIX + phone,
      code,
      RedisConstant.LOGIN_CODE_TTL,
      TimeUnit.MINUTES);
    // 发送验证码
    log.debug("给手机号 {} 发送短信验证码: {}", phone, code);
    return Result.ok();
  }

  /**
   * 登录功能
   *
   * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
   */
  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
    if (loginForm.getPhone() == null || loginForm.getCode() == null) {
      return Result.fail(MessageConstant.PHONE_CODE_IS_NULL);
    }
    if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
      return Result.fail(MessageConstant.INVALID_PHONE_NUMBER);
    }
    if (RegexUtils.isCodeInvalid(loginForm.getCode())) {
      return Result.fail(MessageConstant.INVALID_CODE);
    }
    String phone = loginForm.getPhone();
    String code = loginForm.getCode();
    // 从Redis中获取验证码
    String cachedCode = stringRedisTemplate.opsForValue().get(RedisConstant.LOGIN_CODE_KEY_PREFIX + phone);
    if (!code.equals(cachedCode)) {
      return Result.fail(MessageConstant.INVALID_CODE);
    }
    stringRedisTemplate.delete(RedisConstant.LOGIN_CODE_KEY_PREFIX + phone);
    User user = query().eq(UserEntityConstant.COLUMN_PHONE, loginForm.getPhone()).one();
    if (user == null) {
      user = createUserWithPhone(loginForm.getPhone());
    }
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    Map<String, Object> userDtoMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
      CopyOptions.create().ignoreNullValue().setFieldValueEditor(
        (fieldName, fieldValue) -> fieldValue.toString()
      ));
    String token = UUID.randomUUID().toString(true);
    String tokenKey = RedisConstant.LOGIN_USER_KEY_PREFIX + token;
    stringRedisTemplate.opsForHash().putAll(tokenKey, userDtoMap);
    stringRedisTemplate.expire(tokenKey, RedisConstant.LOGIN_USER_TTL, TimeUnit.MINUTES);
    return Result.ok(token);
  }

  @Override
  public Result queryUserById(Long id) {
    User user = getById(id);
    if (user == null) {
      return Result.ok();
    }
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    return Result.ok(userDTO);
  }

  /**
   * 根据手机号创建用户并设置随机昵称
   *
   * @param phone 手机号
   * @return 用户
   */
  private User createUserWithPhone(String phone) {
    User user = User.builder()
      .phone(phone)
      .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(SystemConstants.USER_NICK_NAME_SUFFIX_LENGTH))
      .build();
    save(user);
    return user;
  }
}
