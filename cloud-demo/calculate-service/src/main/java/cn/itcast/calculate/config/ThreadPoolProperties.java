package cn.itcast.calculate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 线程池配置属性
 * 
 * @author demo
 * @date 2026-02-05
 */
@Data
@Component
@ConfigurationProperties(prefix = "calculate.thread-pool")
public class ThreadPoolProperties {
    
    /**
     * 核心线程数
     */
    private int corePoolSize = 10;
    
    /**
     * 最大线程数
     */
    private int maxPoolSize = 20;
    
    /**
     * 队列容量
     */
    private int queueCapacity = 100;
    
    /**
     * 线程空闲时间（秒）
     */
    private int keepAliveSeconds = 60;
    
    /**
     * 线程名称前缀
     */
    private String threadNamePrefix = "calculate-";
}
