package com.dp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.dto.LoginFormDTO;
import com.dp.dto.Result;
import com.dp.entity.User;
import jakarta.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

  /**
   * 发送短信验证码
   *
   * @param phone 手机号
   * @param session session
   * @return 发送结果
   */
  Result sendCode(String phone, HttpSession session);

  /**
   * 登录功能
   *
   * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
   * @param session session
   * @return 登录结果
   */
  Result login(LoginFormDTO loginForm, HttpSession session);
}
