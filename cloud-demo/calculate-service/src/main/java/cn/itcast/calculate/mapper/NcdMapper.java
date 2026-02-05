package cn.itcast.calculate.mapper;

import cn.itcast.calculate.entity.Ncd;
import org.apache.ibatis.annotations.Param;

/**
 * NCD系数表Mapper
 */
public interface NcdMapper {
    
    /**
     * 查询NCD系数
     */
    Ncd queryNcd(@Param("claimCount") Integer claimCount);
}
