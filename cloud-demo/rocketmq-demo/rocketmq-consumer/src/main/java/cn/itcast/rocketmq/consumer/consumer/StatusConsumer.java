package cn.itcast.rocketmq.consumer.consumer;

import cn.itcast.rocketmq.consumer.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 场景4：顺序消息消费者 - 订单状态变更
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "order-status-topic",
    consumerGroup = "status-consumer",
    consumeMode = ConsumeMode.ORDERLY  // 顺序消费
)
public class StatusConsumer implements RocketMQListener<String> {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public void onMessage(String message) {
        log.info("========== 顺序消息消费 ==========");
        
        String[] parts = message.split(":");
        String orderNo = parts[0];
        Integer status = Integer.parseInt(parts[1]);

        log.info("收到订单状态变更消息：订单号={}, 状态={}", orderNo, status);

        // 更新订单状态
        int rows = orderMapper.updateStatus(orderNo, status);

        if (rows > 0) {
            String statusName = getStatusName(status);
            log.info("订单状态更新成功：订单号={}, 状态={}", orderNo, statusName);
        } else {
            log.warn("订单状态更新失败：订单号={}", orderNo);
        }
        log.info("==================================");
    }

    private String getStatusName(Integer status) {
        switch (status) {
            case 0: return "待支付";
            case 1: return "已支付";
            case 2: return "已发货";
            case 3: return "已完成";
            case -1: return "已取消";
            default: return "未知状态";
        }
    }
}
