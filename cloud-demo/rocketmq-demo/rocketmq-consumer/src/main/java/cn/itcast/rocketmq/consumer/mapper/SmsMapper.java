package cn.itcast.rocketmq.consumer.mapper;

import cn.itcast.rocketmq.pojo.SmsLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 短信Mapper
 */
@Mapper
public interface SmsMapper {

    /**
     * 插入短信记录
     */
    @Insert("INSERT INTO tb_sms_log (order_no, phone, content, send_status, send_time, create_time) " +
            "VALUES (#{orderNo}, #{phone}, #{content}, #{sendStatus}, #{sendTime}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SmsLog smsLog);
}
