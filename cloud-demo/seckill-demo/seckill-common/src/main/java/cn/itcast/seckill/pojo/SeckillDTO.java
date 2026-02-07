package cn.itcast.seckill.pojo;

import lombok.Data;

/**
 * 秒杀请求DTO
 */
@Data
public class SeckillDTO {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 购买数量（默认1）
     */
    private Integer quantity = 1;
}
