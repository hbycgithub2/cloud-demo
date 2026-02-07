package cn.itcast.seckill.consumer.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户秒杀记录Mapper
 */
@Mapper
public interface UserSeckillMapper {

    /**
     * 插入用户秒杀记录
     */
    @Insert("INSERT INTO tb_user_seckill (user_id, product_id, order_no) " +
            "VALUES (#{userId}, #{productId}, #{orderNo})")
    int insert(@Param("userId") Long userId,
               @Param("productId") Long productId,
               @Param("orderNo") String orderNo);
}
