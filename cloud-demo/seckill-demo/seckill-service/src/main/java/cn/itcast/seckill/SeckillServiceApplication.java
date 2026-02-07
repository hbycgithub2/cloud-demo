package cn.itcast.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀服务启动类
 */
@SpringBootApplication
@MapperScan("cn.itcast.seckill.mapper")
public class SeckillServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SeckillServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("秒杀服务启动成功！端口：8092");
        System.out.println("========================================");
    }
}
