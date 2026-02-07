package cn.itcast.rocketmq.producer.mapper;

import cn.itcast.rocketmq.pojo.Order;
import org.apache.ibatis.annotations.*;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper {

    /**
     * 插入订单
     */
    @Insert("INSERT INTO tb_order (order_no, user_id, product_name, price, quantity, status, is_vip, create_time) " +
            "VALUES (#{orderNo}, #{userId}, #{productName}, #{price}, #{quantity}, #{status}, #{isVip}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Order order);

    /**
     * 根据订单号查询订单
     */
    @Select("SELECT * FROM tb_order WHERE order_no = #{orderNo}")
    Order selectByOrderNo(String orderNo);

    /**
     * 更新订单状态
     */
    @Update("UPDATE tb_order SET status = #{status}, update_time = NOW() WHERE order_no = #{orderNo}")
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") Integer status);
}
