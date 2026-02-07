package cn.itcast.rocketmq.consumer.consumer;

import cn.itcast.rocketmq.pojo.Order;
import cn.itcast.rocketmq.pojo.SmsLog;
import cn.itcast.rocketmq.consumer.mapper.SmsMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 场景6：消息过滤消费者 - 只处理VIP订单
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "vip-sms-consumer",
    selectorType = SelectorType.TAG,
    selectorExpression = "VIP"  // 只消费VIP标签的消息
)
public class VipSmsConsumer implements RocketMQListener<Order> {

    @Autowired
    private SmsMapper smsMapper;

    @Override
    public void onMessage(Order order) {
        log.info("========== VIP消息消费 ==========");
        log.info("收到VIP订单消息：订单号={}, 用户ID={}, 商品={}, 金额={}", 
                 order.getOrderNo(), order.getUserId(), order.getProductName(), order.getPrice());

        // 模拟发送VIP专属短信
        String phone = "138****" + (order.getUserId() % 10000);
        String content = String.format("尊敬的VIP用户，您的订单%s已创建成功，商品：%s，金额：%.2f元，享受VIP专属优惠！", 
                                      order.getOrderNo(), order.getProductName(), order.getPrice());

        // 保存短信记录
        SmsLog smsLog = new SmsLog();
        smsLog.setOrderNo(order.getOrderNo());
        smsLog.setPhone(phone);
        smsLog.setContent(content);
        smsLog.setSendStatus(1);  // 已发送
        smsLog.setSendTime(LocalDateTime.now());

        smsMapper.insert(smsLog);
        log.info("VIP短信发送成功：手机号={}, 内容={}", phone, content);
        log.info("==================================");
    }
}
