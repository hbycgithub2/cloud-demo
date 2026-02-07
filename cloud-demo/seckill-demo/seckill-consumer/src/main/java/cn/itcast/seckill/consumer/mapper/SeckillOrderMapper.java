package cn.itcast.seckill.consumer.mapper;

import cn.itcast.seckill.pojo.SeckillOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单Mapper
 */
@Mapper
public interface SeckillOrderMapper {

    /**
     * 插入订单
     */
    @Insert("INSERT INTO tb_seckill_order (order_no, user_id, product_id, product_name, price, quantity, status, create_time) " +
            "VALUES (#{orderNo}, #{userId}, #{productId}, #{productName}, #{price}, #{quantity}, #{status}, #{createTime})")
    int insert(SeckillOrder order);
}
