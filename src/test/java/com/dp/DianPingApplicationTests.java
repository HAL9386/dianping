package com.dp;

import com.dp.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DianPingApplicationTests {

  @Test
  public void testUser() {
    User user = User.builder().phone("13800000000").password("123456").build();
    System.out.println(user);
  }

}
