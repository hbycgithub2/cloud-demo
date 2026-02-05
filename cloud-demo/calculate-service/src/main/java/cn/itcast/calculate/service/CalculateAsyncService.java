package cn.itcast.calculate.service;

import cn.itcast.calculate.entity.Discount;
import cn.itcast.calculate.entity.Ncd;
import cn.itcast.calculate.entity.Rate;
import cn.itcast.calculate.mapper.DiscountMapper;
import cn.itcast.calculate.mapper.NcdMapper;
import cn.itcast.calculate.mapper.RateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * 异步查询服务
 * 使用CompletableFuture并行查询3张表
 * 
 * @author demo
 * @date 2026-02-05
 */
@Slf4j
@Service
public class CalculateAsyncService {
    
    @Autowired
    @Qualifier("calculateExecutor")
    private ThreadPoolTaskExecutor calculateExecutor;
    
    @Autowired
    private RateMapper rateMapper;
    
    @Autowired
    private DiscountMapper discountMapper;
    
    @Autowired
    private NcdMapper ncdMapper;
    
    /**
     * 异步查询费率表
     */
    public CompletableFuture<BigDecimal> queryRateAsync(String kindCode, String carModel, String areaCode) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            log.info("[{}] 开始查询费率表，险种：{}，车型：{}，地区：{}", threadName, kindCode, carModel, areaCode);
            
            // 从数据库查询
            Rate rate = rateMapper.queryRate(kindCode, carModel, areaCode);
            BigDecimal rateValue = rate != null ? rate.getRate() : BigDecimal.ZERO;
            
            long cost = System.currentTimeMillis() - start;
            log.info("[{}] 费率表查询完成，耗时：{}ms，费率：{}", threadName, cost, rateValue);
            
            return rateValue;
        }, calculateExecutor.getThreadPoolExecutor());
    }
    
    /**
     * 异步查询折扣系数表
     */
    public CompletableFuture<BigDecimal> queryDiscountAsync(String channel, String areaCode) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            log.info("[{}] 开始查询折扣系数表，渠道：{}，地区：{}", threadName, channel, areaCode);
            
            // 从数据库查询
            Discount discount = discountMapper.queryDiscount(channel, areaCode);
            BigDecimal discountValue = discount != null ? discount.getDiscount() : BigDecimal.ONE;
            
            long cost = System.currentTimeMillis() - start;
            log.info("[{}] 折扣系数表查询完成，耗时：{}ms，折扣：{}", threadName, cost, discountValue);
            
            return discountValue;
        }, calculateExecutor.getThreadPoolExecutor());
    }
    
    /**
     * 异步查询NCD系数表
     */
    public CompletableFuture<BigDecimal> queryNcdAsync(Integer claimCount) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            log.info("[{}] 开始查询NCD系数表，出险次数：{}", threadName, claimCount);
            
            // 从数据库查询
            Ncd ncd = ncdMapper.queryNcd(claimCount);
            BigDecimal ncdRate = ncd != null ? ncd.getNcdRate() : BigDecimal.ONE;
            
            long cost = System.currentTimeMillis() - start;
            log.info("[{}] NCD系数表查询完成，耗时：{}ms，NCD：{}", threadName, cost, ncdRate);
            
            return ncdRate;
        }, calculateExecutor.getThreadPoolExecutor());
    }
}
