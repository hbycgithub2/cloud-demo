package cn.itcast.rocketmq.pojo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券实体类
 */
@Data
public class Coupon implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String couponName;
    private BigDecimal amount;
    private Integer status;  // 0未使用 1已使用 2已过期
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
