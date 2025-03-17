package com.itcast;

import cn.itcast.demo.cn.itcast.redis.util.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class JedisTest {
    private Jedis jedis;

    @BeforeEach
    void setUp() {
        // 1.建立连接
        //jedis = new Jedis("192.168.26.130", 6379);
        jedis = JedisConnectionFactory.getJedis();
        //、设置密码
        jedis.auth("123456");
        //、选择库
        jedis.select(0);
    }


    @Test
    void testString() {
        String result = jedis.set("name", "虎哥");
        System.out.println("result = " + result);
        //获取数据
        String name = jedis.get("name");
        System.out.println("name = " + name);
    }


    @Test
    void testHash() {
        //插入hash数据
        jedis.hset("user:1", "name", "Jack");
        jedis.hset("user:1", "age", "21");
        //获取
        Map<String, String> map = jedis.hgetAll("user:1");
        System.out.println(map);
    }

    @AfterEach
    void tearDown() {
        if (null != jedis) {
            jedis.close();
        }
    }
}
