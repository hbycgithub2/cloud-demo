package cn.itcast.rocketmq.producer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RocketMQ生产者启动类
 */
@SpringBootApplication
@MapperScan("cn.itcast.rocketmq.producer.mapper")
public class ProducerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
        System.out.println("========================================");
        System.out.println("RocketMQ生产者启动成功！端口：8090");
        System.out.println("========================================");
    }
}
