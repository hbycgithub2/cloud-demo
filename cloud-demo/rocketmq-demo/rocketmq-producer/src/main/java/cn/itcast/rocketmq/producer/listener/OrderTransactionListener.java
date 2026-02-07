package cn.itcast.rocketmq.producer.listener;

import cn.itcast.rocketmq.pojo.Order;
import cn.itcast.rocketmq.producer.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;

/**
 * 事务消息监听器
 */
@Slf4j
@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 执行本地事务
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            Order order = (Order) arg;
            log.info("执行本地事务：保存订单 {}", order.getOrderNo());

            // 保存订单到数据库
            orderMapper.insert(order);

            log.info("本地事务执行成功：订单 {} 保存成功", order.getOrderNo());
            return RocketMQLocalTransactionState.COMMIT;  // 提交事务
        } catch (Exception e) {
            log.error("本地事务执行失败", e);
            return RocketMQLocalTransactionState.ROLLBACK;  // 回滚事务
        }
    }

    /**
     * 事务回查（MQ服务器定期检查事务状态）
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        try {
            Order order = (Order) msg.getPayload();
            log.info("事务回查：检查订单 {} 是否存在", order.getOrderNo());

            // 查询订单是否存在
            Order dbOrder = orderMapper.selectByOrderNo(order.getOrderNo());

            if (dbOrder != null) {
                log.info("事务回查：订单 {} 存在，提交事务", order.getOrderNo());
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.info("事务回查：订单 {} 不存在，回滚事务", order.getOrderNo());
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.error("事务回查失败", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
}
