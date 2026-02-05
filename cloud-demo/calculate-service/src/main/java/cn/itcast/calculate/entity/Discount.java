package cn.itcast.calculate.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 折扣系数表实体
 */
@Data
public class Discount {
    private String channel;
    private String areaCode;
    private BigDecimal discount;
}
