package cn.itcast.rocketmq.consumer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RocketMQ消费者启动类
 */
@SpringBootApplication
@MapperScan("cn.itcast.rocketmq.consumer.mapper")
public class ConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
        System.out.println("========================================");
        System.out.println("RocketMQ消费者启动成功！端口：8091");
        System.out.println("========================================");
    }
}
