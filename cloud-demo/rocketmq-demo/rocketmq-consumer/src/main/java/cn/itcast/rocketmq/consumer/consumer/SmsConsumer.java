package cn.itcast.rocketmq.consumer.consumer;

import cn.itcast.rocketmq.pojo.Order;
import cn.itcast.rocketmq.pojo.SmsLog;
import cn.itcast.rocketmq.consumer.mapper.SmsMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 场景1：普通消息消费者 - 发送短信通知
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "sms-consumer"
)
public class SmsConsumer implements RocketMQListener<Order> {

    @Autowired
    private SmsMapper smsMapper;

    @Override
    public void onMessage(Order order) {
        log.info("========== 普通消息消费 ==========");
        log.info("收到订单消息：订单号={}, 用户ID={}, 商品={}, 金额={}", 
                 order.getOrderNo(), order.getUserId(), order.getProductName(), order.getPrice());

        // 模拟发送短信
        String phone = "138****" + (order.getUserId() % 10000);
        String content = String.format("您的订单%s已创建成功，商品：%s，金额：%.2f元", 
                                      order.getOrderNo(), order.getProductName(), order.getPrice());

        // 保存短信记录
        SmsLog smsLog = new SmsLog();
        smsLog.setOrderNo(order.getOrderNo());
        smsLog.setPhone(phone);
        smsLog.setContent(content);
        smsLog.setSendStatus(1);  // 已发送
        smsLog.setSendTime(LocalDateTime.now());

        smsMapper.insert(smsLog);
        log.info("短信发送成功：手机号={}, 内容={}", phone, content);
        log.info("==================================");
    }
}
