package cn.itcast.calculate.pojo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 算费请求参数
 * 
 * @author demo
 * @date 2026-02-05
 */
@Data
public class CalculateRequest {
    
    /**
     * 险种代码
     */
    private String kindCode;
    
    /**
     * 保额（元）
     */
    private BigDecimal amount;
}
