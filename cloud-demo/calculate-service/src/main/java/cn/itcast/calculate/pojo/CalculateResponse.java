package cn.itcast.calculate.pojo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 算费响应结果
 * 
 * @author demo
 * @date 2026-02-05
 */
@Data
public class CalculateResponse {
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 险种代码
     */
    private String kindCode;
    
    /**
     * 保额（元）
     */
    private BigDecimal amount;
    
    /**
     * 保费（元）
     */
    private BigDecimal premium;
    
    /**
     * 基础费率
     */
    private BigDecimal rate;
    
    /**
     * 折扣系数
     */
    private BigDecimal discount;
    
    /**
     * NCD系数（无赔款优待系数）
     */
    private BigDecimal ncdRate;
    
    /**
     * 消息
     */
    private String message;
}
