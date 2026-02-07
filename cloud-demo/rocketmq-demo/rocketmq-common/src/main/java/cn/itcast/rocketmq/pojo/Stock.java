package cn.itcast.rocketmq.pojo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存实体类
 */
@Data
public class Stock implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String productName;
    private Integer stock;
    private Integer version;
    private LocalDateTime updateTime;
}
