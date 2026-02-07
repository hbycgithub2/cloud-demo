package cn.itcast.rocketmq.consumer.mapper;

import cn.itcast.rocketmq.pojo.Coupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 优惠券Mapper
 */
@Mapper
public interface CouponMapper {

    /**
     * 插入优惠券
     */
    @Insert("INSERT INTO tb_coupon (user_id, coupon_name, amount, status, expire_time, create_time) " +
            "VALUES (#{userId}, #{couponName}, #{amount}, #{status}, #{expireTime}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Coupon coupon);
}
