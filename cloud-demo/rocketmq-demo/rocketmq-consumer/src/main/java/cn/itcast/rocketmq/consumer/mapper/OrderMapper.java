package cn.itcast.rocketmq.consumer.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper {

    /**
     * 更新订单状态
     */
    @Update("UPDATE tb_order SET status = #{status}, update_time = NOW() WHERE order_no = #{orderNo}")
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") Integer status);
}
