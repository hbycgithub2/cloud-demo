package cn.itcast.rocketmq.pojo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存扣减记录实体类
 */
@Data
public class StockLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private String productName;
    private Integer quantity;
    private Integer beforeStock;
    private Integer afterStock;
    private LocalDateTime createTime;
}
