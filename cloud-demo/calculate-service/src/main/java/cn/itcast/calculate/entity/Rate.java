package cn.itcast.calculate.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 费率表实体
 */
@Data
public class Rate {
    private String kindCode;
    private String carModel;
    private String areaCode;
    private BigDecimal rate;
}
