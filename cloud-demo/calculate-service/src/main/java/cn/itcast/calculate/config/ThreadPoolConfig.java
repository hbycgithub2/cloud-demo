package cn.itcast.calculate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置
 * 用于CompletableFuture异步查询
 * 
 * @author demo
 * @date 2026-02-05
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {
    
    @Autowired
    private ThreadPoolProperties properties;
    
    /**
     * 算费专用线程池
     * 
     * 配置来源：application.yml中的calculate.thread-pool.*
     * 
     * 性能提升：
     * - 串行查询：30ms + 30ms + 30ms = 90ms
     * - 并行查询：max(30ms, 30ms, 30ms) = 30ms
     * - 提升3倍
     */
    @Bean("calculateExecutor")
    public ThreadPoolTaskExecutor calculateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 从配置文件读取参数
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        
        // 拒绝策略（队列满了，主线程执行）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待任务完成后关闭（优雅停机）
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // 初始化
        executor.initialize();
        
        log.info("算费线程池初始化完成：核心线程{}，最大线程{}，队列容量{}", 
                properties.getCorePoolSize(), 
                properties.getMaxPoolSize(),
                properties.getQueueCapacity());
        
        return executor;
    }
}
