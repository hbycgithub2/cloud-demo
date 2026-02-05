package cn.itcast.calculate;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 即时算费服务启动类
 * 
 * @author demo
 * @date 2026-02-05
 */
@SpringBootApplication
@MapperScan("cn.itcast.calculate.mapper")
public class CalculateApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(CalculateApplication.class, args);
    }
}
