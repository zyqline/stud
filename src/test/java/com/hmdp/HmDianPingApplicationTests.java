package com.hmdp;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.impl.BlogServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    BlogServiceImpl blogService;

          @Test
          public void test1(){

             String ids="14,13";
              List<Blog> id = blogService.query().in("id", "14,13").last("ORDER BY FIELD("+ids+")").list();
              System.out.println(id);
          }
}
