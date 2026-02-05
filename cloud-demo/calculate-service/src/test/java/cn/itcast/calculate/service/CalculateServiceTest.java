package cn.itcast.calculate.service;

import cn.itcast.calculate.pojo.CalculateRequest;
import cn.itcast.calculate.pojo.CalculateResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;

/**
 * 算费服务测试类
 * 
 * @author demo
 * @date 2026-02-05
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class CalculateServiceTest {
    
    @Autowired
    private CalculateService calculateService;
    
    /**
     * 测试串行查询
     */
    @Test
    public void testSerialCalculate() {
        log.info("========================================");
        log.info("测试串行查询");
        log.info("========================================");
        
        // 准备请求参数
        CalculateRequest request = new CalculateRequest();
        request.setKindCode("050200");
        request.setAmount(new BigDecimal("100000"));
        
        // 执行串行查询
        long start = System.currentTimeMillis();
        CalculateResponse response = calculateService.calculate(request);
        long cost = System.currentTimeMillis() - start;
        
        // 打印结果
        log.info("串行查询结果：");
        log.info("  险种代码：{}", response.getKindCode());
        log.info("  保额：{} 元", response.getAmount());
        log.info("  保费：{} 元", response.getPremium());
        log.info("  费率：{}", response.getRate());
        log.info("  折扣：{}", response.getDiscount());
        log.info("  NCD：{}", response.getNcdRate());
        log.info("  总耗时：{} ms", cost);
        log.info("========================================");
    }
    
    /**
     * 测试并行查询
     */
    @Test
    public void testParallelCalculate() {
        log.info("========================================");
        log.info("测试并行查询");
        log.info("========================================");
        
        // 准备请求参数
        CalculateRequest request = new CalculateRequest();
        request.setKindCode("050200");
        request.setAmount(new BigDecimal("100000"));
        
        // 执行并行查询
        long start = System.currentTimeMillis();
        CalculateResponse response = calculateService.calculateParallel(request);
        long cost = System.currentTimeMillis() - start;
        
        // 打印结果
        log.info("并行查询结果：");
        log.info("  险种代码：{}", response.getKindCode());
        log.info("  保额：{} 元", response.getAmount());
        log.info("  保费：{} 元", response.getPremium());
        log.info("  费率：{}", response.getRate());
        log.info("  折扣：{}", response.getDiscount());
        log.info("  NCD：{}", response.getNcdRate());
        log.info("  总耗时：{} ms", cost);
        log.info("========================================");
    }
    
    /**
     * 对比串行和并行性能
     */
    @Test
    public void testPerformanceComparison() {
        log.info("========================================");
        log.info("性能对比测试（执行10次）");
        log.info("========================================");
        
        CalculateRequest request = new CalculateRequest();
        request.setKindCode("050200");
        request.setAmount(new BigDecimal("100000"));
        
        long serialTotal = 0;
        long parallelTotal = 0;
        int times = 10;
        
        // 串行查询10次
        for (int i = 0; i < times; i++) {
            long start = System.currentTimeMillis();
            calculateService.calculate(request);
            long cost = System.currentTimeMillis() - start;
            serialTotal += cost;
            log.info("串行查询第{}次，耗时：{} ms", i + 1, cost);
        }
        
        log.info("----------------------------------------");
        
        // 并行查询10次
        for (int i = 0; i < times; i++) {
            long start = System.currentTimeMillis();
            calculateService.calculateParallel(request);
            long cost = System.currentTimeMillis() - start;
            parallelTotal += cost;
            log.info("并行查询第{}次，耗时：{} ms", i + 1, cost);
        }
        
        // 统计结果
        long serialAvg = serialTotal / times;
        long parallelAvg = parallelTotal / times;
        double improvement = (double) (serialAvg - parallelAvg) / serialAvg * 100;
        
        log.info("========================================");
        log.info("性能对比结果：");
        log.info("  串行查询平均耗时：{} ms", serialAvg);
        log.info("  并行查询平均耗时：{} ms", parallelAvg);
        log.info("  性能提升：{} %", String.format("%.2f", improvement));
        log.info("========================================");
    }
}
