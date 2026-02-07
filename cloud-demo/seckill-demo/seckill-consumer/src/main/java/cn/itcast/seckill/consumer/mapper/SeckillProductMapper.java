package cn.itcast.seckill.consumer.mapper;

import cn.itcast.seckill.pojo.SeckillProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 秒杀商品Mapper
 */
@Mapper
public interface SeckillProductMapper {

    /**
     * 根据ID查询秒杀商品
     */
    @Select("SELECT * FROM tb_seckill_product WHERE id = #{id}")
    SeckillProduct selectById(@Param("id") Long id);

    /**
     * 扣减库存（乐观锁）
     */
    int reduceStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
