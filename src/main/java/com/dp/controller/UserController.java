package com.dp.controller;


import cn.hutool.core.util.RandomUtil;
import com.dp.constant.MessageConstant;
import com.dp.dto.LoginFormDTO;
import com.dp.dto.Result;
import com.dp.entity.User;
import com.dp.entity.UserInfo;
import com.dp.service.IUserInfoService;
import com.dp.service.IUserService;
import com.dp.utils.RegexUtils;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

  private final IUserService userService;
  private final IUserInfoService userInfoService;

  public UserController(IUserService userService, IUserInfoService userInfoService) {
    this.userService = userService;
    this.userInfoService = userInfoService;
  }

  /**
   * 发送手机验证码
   */
  @PostMapping("code")
  public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
    if (!RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail(MessageConstant.INVALID_PHONE_NUMBER);
    }
    // 发送短信验证码
    String code = RandomUtil.randomString(6);
    log.info("发送短信验证码: {}", code);
    // 保存验证码
    session.setAttribute("code", code);
    return Result.ok();
  }

  /**
   * 登录功能
   *
   * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
   */
  @PostMapping("/login")
  public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
    // TODO 实现登录功能
    if (loginForm.getPhone() == null || loginForm.getCode() == null) {
      return Result.fail(MessageConstant.PHONE_CODE_IS_NULL);
    }
    if (!RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
      return Result.fail(MessageConstant.INVALID_PHONE_NUMBER);
    }
    if (!loginForm.getCode().equals(session.getAttribute("code"))) {
      return Result.fail(MessageConstant.INVALID_CODE);
    }
    // 登录成功，删除验证码
    session.removeAttribute("code");
    User user = query().eq("phone", loginForm.getPhone()).one();
    if (user == null) {
      user = userService.createUserWithPhone(loginForm.getPhone());
    }
    session.setAttribute("user", user);
    return Result.ok();
  }

  /**
   * 登出功能
   *
   * @return 无
   */
  @PostMapping("/logout")
  public Result logout() {
    // TODO 实现登出功能
    return Result.fail("功能未完成");
  }

  @GetMapping("/me")
  public Result me() {
    // TODO 获取当前登录的用户并返回
    return Result.fail("功能未完成");
  }

  @GetMapping("/info/{id}")
  public Result info(@PathVariable("id") Long userId) {
    // 查询详情
    UserInfo info = userInfoService.getById(userId);
    if (info == null) {
      // 没有详情，应该是第一次查看详情
      return Result.ok();
    }
    info.setCreateTime(null);
    info.setUpdateTime(null);
    // 返回
    return Result.ok(info);
  }
}
