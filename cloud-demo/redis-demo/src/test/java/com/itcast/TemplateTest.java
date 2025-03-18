package com.itcast;

import cn.itcast.demo.cn.itcast.redis.RedisApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest(classes = RedisApplication.class)
public class TemplateTest {
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

//    @Autowired
//    private RedisTemplate redisTemplate;

    @Test
    public void testString() {
        System.out.println(111);
        // 写入一条String数据
        redisTemplate.opsForValue().set("name", "虎哥18");
        // 获取string数据
        Object name = redisTemplate.opsForValue().get("name");
        System.out.println("name = " + name);
    }
}
