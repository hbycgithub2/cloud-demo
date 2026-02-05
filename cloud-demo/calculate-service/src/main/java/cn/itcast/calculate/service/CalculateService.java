package cn.itcast.calculate.service;

import cn.itcast.calculate.pojo.CalculateRequest;
import cn.itcast.calculate.pojo.CalculateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;

/**
 * 算费服务
 * 
 * @author demo
 * @date 2026-02-05
 */
@Slf4j
@Service
public class CalculateService {
    
    @Autowired
    private CalculateAsyncService calculateAsyncService;
    
    /**
     * 即时算费
     * 
     * @param request 请求参数
     * @return 算费结果
     */
    public CalculateResponse calculate(CalculateRequest request) {
        log.info("开始计算保费，险种：{}，保额：{}", request.getKindCode(), request.getAmount());
        
        CalculateResponse response = new CalculateResponse();
        
        try {
            // 获取费率（这里使用固定值，实际应该从数据库查询）
            BigDecimal rate = getRate(request.getKindCode());
            
            // 获取折扣系数（这里使用固定值，实际应该根据业务规则计算）
            BigDecimal discount = getDiscount(request.getKindCode());
            
            // 获取NCD系数（这里使用固定值，实际应该根据历史出险记录计算）
            BigDecimal ncdRate = getNcdRate();
            
            // 计算保费：保费 = 保额 × 基础费率 × 折扣系数 × NCD系数
            BigDecimal premium = request.getAmount()
                    .multiply(rate)
                    .multiply(discount)
                    .multiply(ncdRate)
                    .setScale(2, RoundingMode.HALF_UP);
            
            // 封装结果
            response.setSuccess(true);
            response.setKindCode(request.getKindCode());
            response.setAmount(request.getAmount());
            response.setPremium(premium);
            response.setRate(rate);
            response.setDiscount(discount);
            response.setNcdRate(ncdRate);
            response.setMessage("计算成功");
            
            log.info("保费计算成功，保费：{}元", premium);
            
        } catch (Exception e) {
            log.error("保费计算失败", e);
            response.setSuccess(false);
            response.setMessage("计算失败：" + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 获取费率
     * 实际应该从数据库查询
     */
    private BigDecimal getRate(String kindCode) {
        // 这里使用固定值，实际应该从数据库的费率表查询
        return new BigDecimal("0.014320");
    }
    
    /**
     * 获取折扣系数
     * 实际应该根据业务规则计算
     */
    private BigDecimal getDiscount(String kindCode) {
        // 这里使用固定值，实际应该根据渠道、活动等因素计算
        return new BigDecimal("0.7225");
    }
    
    /**
     * 获取NCD系数（无赔款优待系数）
     * 实际应该根据历史出险记录计算
     */
    private BigDecimal getNcdRate() {
        // 这里使用固定值，实际应该根据历史出险记录计算
        // 1年无出险：0.85，2年无出险：0.7，3年及以上无出险：0.6
        return new BigDecimal("1.0000");
    }
    
    /**
     * 即时算费（并行查询版本）
     * 使用CompletableFuture并行查询3张表
     * 
     * 性能对比：
     * - 串行查询：30ms + 30ms + 30ms = 90ms
     * - 并行查询：max(30ms, 30ms, 30ms) = 30ms
     * - 性能提升：3倍
     */
    public CalculateResponse calculateParallel(CalculateRequest request) {
        long start = System.currentTimeMillis();
        log.info("开始并行计算保费，险种：{}，保额：{}", request.getKindCode(), request.getAmount());
        
        CalculateResponse response = new CalculateResponse();
        
        try {
            // 并行查询3张表（从数据库查询）
            // 默认参数：车型GL8，地区3201，渠道WECHAT，出险次数0
            CompletableFuture<BigDecimal> rateFuture = calculateAsyncService.queryRateAsync(
                request.getKindCode(), "GL8", "3201");
            CompletableFuture<BigDecimal> discountFuture = calculateAsyncService.queryDiscountAsync(
                "WECHAT", "3201");
            CompletableFuture<BigDecimal> ncdFuture = calculateAsyncService.queryNcdAsync(0);
            
            // 等待所有查询完成
            CompletableFuture.allOf(rateFuture, discountFuture, ncdFuture).join();
            
            // 获取查询结果
            BigDecimal rate = rateFuture.get();
            BigDecimal discount = discountFuture.get();
            BigDecimal ncdRate = ncdFuture.get();
            
            // 计算保费
            BigDecimal premium = request.getAmount()
                    .multiply(rate)
                    .multiply(discount)
                    .multiply(ncdRate)
                    .setScale(2, RoundingMode.HALF_UP);
            
            // 封装结果
            response.setSuccess(true);
            response.setKindCode(request.getKindCode());
            response.setAmount(request.getAmount());
            response.setPremium(premium);
            response.setRate(rate);
            response.setDiscount(discount);
            response.setNcdRate(ncdRate);
            response.setMessage("计算成功（并行查询）");
            
            long cost = System.currentTimeMillis() - start;
            log.info("并行保费计算成功，保费：{}元，总耗时：{}ms", premium, cost);
            
        } catch (Exception e) {
            log.error("并行保费计算失败", e);
            response.setSuccess(false);
            response.setMessage("计算失败：" + e.getMessage());
        }
        
        return response;
    }
}
