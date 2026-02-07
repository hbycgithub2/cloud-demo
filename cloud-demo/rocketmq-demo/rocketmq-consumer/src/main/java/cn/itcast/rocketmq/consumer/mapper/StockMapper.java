package cn.itcast.rocketmq.consumer.mapper;

import cn.itcast.rocketmq.pojo.Stock;
import cn.itcast.rocketmq.pojo.StockLog;
import org.apache.ibatis.annotations.*;

/**
 * 库存Mapper
 */
@Mapper
public interface StockMapper {

    /**
     * 根据商品名称查询库存
     */
    @Select("SELECT * FROM tb_stock WHERE product_name = #{productName}")
    Stock selectByProductName(String productName);

    /**
     * 扣减库存（乐观锁）
     */
    @Update("UPDATE tb_stock SET stock = stock - #{quantity}, version = version + 1, update_time = NOW() " +
            "WHERE product_name = #{productName} AND stock >= #{quantity} AND version = #{version}")
    int deductStock(@Param("productName") String productName, 
                    @Param("quantity") Integer quantity, 
                    @Param("version") Integer version);

    /**
     * 插入库存扣减记录
     */
    @Insert("INSERT INTO tb_stock_log (order_no, product_name, quantity, before_stock, after_stock, create_time) " +
            "VALUES (#{orderNo}, #{productName}, #{quantity}, #{beforeStock}, #{afterStock}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertLog(StockLog stockLog);
}
