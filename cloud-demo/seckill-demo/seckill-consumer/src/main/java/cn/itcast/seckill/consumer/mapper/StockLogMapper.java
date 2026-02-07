package cn.itcast.seckill.consumer.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存日志Mapper
 */
@Mapper
public interface StockLogMapper {

    /**
     * 插入库存日志
     */
    @Insert("INSERT INTO tb_stock_log (order_no, product_id, product_name, quantity, before_stock, after_stock, type) " +
            "VALUES (#{orderNo}, #{productId}, #{productName}, #{quantity}, #{beforeStock}, #{afterStock}, #{type})")
    int insert(@Param("orderNo") String orderNo,
               @Param("productId") Long productId,
               @Param("productName") String productName,
               @Param("quantity") Integer quantity,
               @Param("beforeStock") Integer beforeStock,
               @Param("afterStock") Integer afterStock,
               @Param("type") Integer type);
}
