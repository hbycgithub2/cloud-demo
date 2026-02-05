package cn.itcast.calculate.mapper;

import cn.itcast.calculate.entity.Rate;
import org.apache.ibatis.annotations.Param;

/**
 * 费率表Mapper
 */
public interface RateMapper {
    
    /**
     * 查询费率
     */
    Rate queryRate(@Param("kindCode") String kindCode, 
                   @Param("carModel") String carModel, 
                   @Param("areaCode") String areaCode);
}
