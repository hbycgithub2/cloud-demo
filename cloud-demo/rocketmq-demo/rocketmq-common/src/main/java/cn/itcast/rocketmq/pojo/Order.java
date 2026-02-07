package cn.itcast.rocketmq.pojo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 */
@Data
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private Integer status;  // 0待支付 1已支付 -1已取消
    private Integer isVip;   // 0普通 1VIP
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
