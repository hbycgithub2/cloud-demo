package cn.itcast.calculate.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * NCD系数表实体
 */
@Data
public class Ncd {
    private Integer claimCount;
    private BigDecimal ncdRate;
}
