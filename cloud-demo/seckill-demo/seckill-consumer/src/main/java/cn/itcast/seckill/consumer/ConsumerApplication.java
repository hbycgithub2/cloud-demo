package cn.itcast.seckill.consumer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀消费者启动类
 */
@SpringBootApplication
@MapperScan("cn.itcast.seckill.consumer.mapper")
public class ConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
        System.out.println("========================================");
        System.out.println("秒杀消费者启动成功！端口：8093");
        System.out.println("========================================");
    }
}
