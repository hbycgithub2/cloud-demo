package cn.itcast.rocketmq.consumer.consumer;

import cn.itcast.rocketmq.consumer.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 场景2：延迟消息消费者 - 自动取消超时订单
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "cancel-topic",
    consumerGroup = "cancel-consumer"
)
public class CancelConsumer implements RocketMQListener<String> {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public void onMessage(String orderNo) {
        log.info("========== 延迟消息消费（30分钟后） ==========");
        log.info("收到取消订单消息：订单号={}", orderNo);

        // 更新订单状态为已取消
        int rows = orderMapper.updateStatus(orderNo, -1);

        if (rows > 0) {
            log.info("订单自动取消成功：订单号={}", orderNo);
        } else {
            log.warn("订单取消失败（可能已支付）：订单号={}", orderNo);
        }
        log.info("==========================================");
    }
}
