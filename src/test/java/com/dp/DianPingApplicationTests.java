package com.dp;

import cn.hutool.core.bean.BeanUtil;
import com.dp.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest
class DianPingApplicationTests {

  @Test
  public void testUser() {
    User user = User.builder().phone("13800000000").password("123456").build();
    System.out.println(user);
  }

  @Test
  public void testBeanToMap() {
    User user = User.builder().phone("13800000000").password("123456").build();
    Map<String, Object> userMap = BeanUtil.beanToMap(user);
    System.out.println(userMap);
  }
}
