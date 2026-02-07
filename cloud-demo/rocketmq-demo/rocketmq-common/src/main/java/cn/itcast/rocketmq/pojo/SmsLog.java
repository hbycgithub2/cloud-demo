package cn.itcast.rocketmq.pojo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短信记录实体类
 */
@Data
public class SmsLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private String phone;
    private String content;
    private Integer sendStatus;  // 0待发送 1已发送 2发送失败
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
}
