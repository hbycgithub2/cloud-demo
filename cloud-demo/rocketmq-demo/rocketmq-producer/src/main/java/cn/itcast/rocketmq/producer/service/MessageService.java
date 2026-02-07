package cn.itcast.rocketmq.producer.service;

import cn.itcast.rocketmq.pojo.Order;
import cn.itcast.rocketmq.producer.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 消息发送服务
 */
@Slf4j
@Service
public class MessageService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 场景1：普通消息 - 发送短信通知
     */
    public String sendNormalMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
        // 1. 生成订单号
        String orderNo = generateOrderNo();

        // 2. 创建订单对象
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductName(productName);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setStatus(0);  // 待支付
        order.setIsVip(0);   // 普通用户

        // 3. 保存订单到数据库
        orderMapper.insert(order);
        log.info("订单创建成功：{}", orderNo);

        // 4. 发送普通消息到MQ
        rocketMQTemplate.convertAndSend("order-topic", order);
        log.info("普通消息发送成功：{}", orderNo);

        return orderNo;
    }

    /**
     * 场景2：延迟消息 - 自动取消超时订单
     */
    public String sendDelayMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
        // 1. 生成订单号
        String orderNo = generateOrderNo();

        // 2. 创建订单对象
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductName(productName);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setStatus(0);  // 待支付
        order.setIsVip(0);

        // 3. 保存订单到数据库
        orderMapper.insert(order);
        log.info("订单创建成功（待支付）：{}", orderNo);

        // 4. 发送延迟消息（30分钟后执行）
        Message<String> message = MessageBuilder
                .withPayload(orderNo)
                .build();

        // 延迟级别：16 = 30分钟
        // 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m
        SendResult result = rocketMQTemplate.syncSend("cancel-topic", message, 3000, 16);
        log.info("延迟消息发送成功：{}，30分钟后自动取消", orderNo);

        return orderNo;
    }

    /**
     * 场景3：事务消息 - 订单和库存一致性
     */
    public String sendTransactionMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
        // 1. 生成订单号
        String orderNo = generateOrderNo();

        // 2. 创建订单对象
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductName(productName);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setStatus(0);
        order.setIsVip(0);

        // 3. 发送事务消息
        Message<Order> message = MessageBuilder
                .withPayload(order)
                .build();

        rocketMQTemplate.sendMessageInTransaction("stock-topic", message, order);
        log.info("事务消息发送成功：{}", orderNo);

        return orderNo;
    }

    /**
     * 场景4：顺序消息 - 订单状态变更
     */
    public String sendOrderlyMessage(String orderNo, Integer status) {
        // 发送顺序消息（同一个orderId的消息发到同一个队列）
        String message = orderNo + ":" + status;
        rocketMQTemplate.syncSendOrderly("order-status-topic", message, orderNo);
        log.info("顺序消息发送成功：订单号={}, 状态={}", orderNo, status);

        return "顺序消息发送成功";
    }

    /**
     * 场景5：批量消息 - 批量发送优惠券
     */
    public String sendBatchMessage(List<Long> userIds) {
        int totalSent = 0;
        
        // 循环发送消息
        for (Long userId : userIds) {
            Message<Long> message = MessageBuilder.withPayload(userId).build();
            rocketMQTemplate.syncSend("coupon-topic", message);
            totalSent++;
            
            // 每100条打印一次日志
            if (totalSent % 100 == 0) {
                log.info("批量消息发送中：已发送 {} 条", totalSent);
            }
        }

        log.info("批量消息发送完成：共发送 {} 条", totalSent);
        return "批量发送 " + userIds.size() + " 条消息成功";
    }

    /**
     * 场景6：消息过滤 - 只处理VIP订单
     */
    public String sendVipMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
        // 1. 生成订单号
        String orderNo = generateOrderNo();

        // 2. 创建订单对象
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductName(productName);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setStatus(0);
        order.setIsVip(1);  // VIP用户

        // 3. 保存订单到数据库
        orderMapper.insert(order);
        log.info("VIP订单创建成功：{}", orderNo);

        // 4. 发送消息时打标签
        Message<Order> message = MessageBuilder
                .withPayload(order)
                .setHeader(RocketMQHeaders.TAGS, "VIP")
                .build();

        rocketMQTemplate.syncSend("order-topic", message);
        log.info("VIP消息发送成功：{}", orderNo);

        return orderNo;
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                (int) (Math.random() * 10000);
    }
}
