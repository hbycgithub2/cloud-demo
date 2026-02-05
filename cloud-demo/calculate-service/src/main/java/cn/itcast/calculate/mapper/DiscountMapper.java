package cn.itcast.calculate.mapper;

import cn.itcast.calculate.entity.Discount;
import org.apache.ibatis.annotations.Param;

/**
 * 折扣系数表Mapper
 */
public interface DiscountMapper {
    
    /**
     * 查询折扣系数
     */
    Discount queryDiscount(@Param("channel") String channel, 
                          @Param("areaCode") String areaCode);
}
