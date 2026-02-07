# RocketMQ：消息队列

## RocketMQ是什么
阿里开源的分布式消息中间件，支持高并发、高可用、消息可靠性，适合大型分布式系统。

**核心功能：** 异步解耦、削峰填谷、消息可靠性、顺序消息、延迟消息、事务消息

---

## RocketMQ vs RabbitMQ

| 对比项 | RocketMQ | RabbitMQ |
|--------|----------|----------|
| **性能** | 100K+ QPS | 10K QPS |
| **适用场景** | 大型高并发项目 | 中小型项目 |
| **特性** | 延迟消息、事务消息、顺序消息 | 功能简单 |
| **学习成本** | 中等 | 简单 |
| **公司使用** | 阿里、京东、滴滴 | 中小公司 |

**面试推荐：** RocketMQ（技术深度高，大厂常用，高并发场景更有说服力）

---

## 6大核心功能测试场景

### 场景1：普通消息 - 发送短信通知
**业务场景：** 用户下单成功后，异步发送短信通知

**生产者（订单服务）：**
```java
@Service
public class OrderService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void createOrder(Order order) {
        // 1. 保存订单到数据库
        orderMapper.insert(order);
        
        // 2. 发送消息到MQ（异步）
        rocketMQTemplate.convertAndSend("order-topic", order);
    }
}
```

**消费者（短信服务）：**
```java
@Service
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "sms-consumer"
)
public class SmsConsumer implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        // 发送短信
        sendSms(order.getPhone(), "订单创建成功");
    }
}
```

---

### 场景2：延迟消息 - 自动取消超时订单
**业务场景：** 用户下单后30分钟未支付，自动取消订单

**生产者（订单服务）：**
```java
@Service
public class OrderService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void createOrder(Order order) {
        // 1. 保存订单（状态：待支付）
        orderMapper.insert(order);
        
        // 2. 发送延迟消息（30分钟后执行）
        Message<String> message = MessageBuilder
            .withPayload(order.getId())
            .build();
        
        rocketMQTemplate.syncSend(
            "cancel-topic",
            message,
            3000,  // 超时时间
            16     // 延迟级别：30分钟（1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m）
        );
    }
}
```

**消费者（取消订单服务）：**
```java
@Service
@RocketMQMessageListener(
    topic = "cancel-topic",
    consumerGroup = "cancel-consumer"
)
public class CancelConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String orderId) {
        // 查询订单状态
        Order order = orderMapper.selectById(orderId);
        
        // 如果还是待支付，自动取消
        if (order.getStatus() == 0) {
            order.setStatus(-1);  // 已取消
            orderMapper.updateById(order);
        }
    }
}
```

---

### 场景3：事务消息 - 订单和库存一致性
**业务场景：** 创建订单和扣减库存必须同时成功或同时失败

**生产者（订单服务）：**
```java
@Service
public class OrderService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void createOrder(Order order) {
        // 发送事务消息
        rocketMQTemplate.sendMessageInTransaction(
            "stock-topic",
            MessageBuilder.withPayload(order).build(),
            order  // 传递给本地事务的参数
        );
    }
}

@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {
    @Autowired
    private OrderMapper orderMapper;
    
    // 执行本地事务
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            Order order = (Order) arg;
            // 保存订单到数据库
            orderMapper.insert(order);
            return RocketMQLocalTransactionState.COMMIT;  // 提交事务
        } catch (Exception e) {
            return RocketMQLocalTransactionState.ROLLBACK;  // 回滚事务
        }
    }
    
    // 事务回查（MQ服务器定期检查事务状态）
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 查询订单是否存在
        Order order = orderMapper.selectById(orderId);
        return order != null ? 
            RocketMQLocalTransactionState.COMMIT : 
            RocketMQLocalTransactionState.ROLLBACK;
    }
}
```

**消费者（库存服务）：**
```java
@Service
@RocketMQMessageListener(
    topic = "stock-topic",
    consumerGroup = "stock-consumer"
)
public class StockConsumer implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        // 扣减库存
        stockMapper.deduct(order.getProductId(), order.getQuantity());
    }
}
```

---

### 场景4：顺序消息 - 订单状态变更
**业务场景：** 订单状态必须按顺序变更：创建 → 支付 → 发货 → 完成

**生产者（订单服务）：**
```java
@Service
public class OrderService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void updateOrderStatus(String orderId, int status) {
        // 发送顺序消息（同一个orderId的消息发到同一个队列）
        rocketMQTemplate.syncSendOrderly(
            "order-status-topic",
            orderId + ":" + status,
            orderId  // 用orderId作为分区键，保证同一订单的消息顺序
        );
    }
}
```

**消费者（订单状态服务）：**
```java
@Service
@RocketMQMessageListener(
    topic = "order-status-topic",
    consumerGroup = "status-consumer",
    consumeMode = ConsumeMode.ORDERLY  // 顺序消费
)
public class StatusConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String message) {
        String[] parts = message.split(":");
        String orderId = parts[0];
        int status = Integer.parseInt(parts[1]);
        
        // 更新订单状态
        orderMapper.updateStatus(orderId, status);
    }
}
```

---

### 场景5：批量消息 - 批量发送优惠券
**业务场景：** 活动期间给10000个用户批量发送优惠券

**生产者（营销服务）：**
```java
@Service
public class CouponService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void sendCoupons(List<String> userIds) {
        // 批量发送消息（一次最多1000条）
        List<Message<String>> messages = new ArrayList<>();
        for (String userId : userIds) {
            messages.add(MessageBuilder.withPayload(userId).build());
            
            // 每1000条发送一次
            if (messages.size() == 1000) {
                rocketMQTemplate.syncSend("coupon-topic", messages);
                messages.clear();
            }
        }
        
        // 发送剩余消息
        if (!messages.isEmpty()) {
            rocketMQTemplate.syncSend("coupon-topic", messages);
        }
    }
}
```

**消费者（优惠券服务）：**
```java
@Service
@RocketMQMessageListener(
    topic = "coupon-topic",
    consumerGroup = "coupon-consumer"
)
public class CouponConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String userId) {
        // 给用户发放优惠券
        couponMapper.insert(userId, couponId);
    }
}
```

---

### 场景6：消息过滤 - 只处理VIP订单
**业务场景：** 只给VIP用户发送专属优惠短信

**生产者（订单服务）：**
```java
@Service
public class OrderService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void createOrder(Order order) {
        // 发送消息时打标签
        Message<Order> message = MessageBuilder
            .withPayload(order)
            .setHeader(RocketMQHeaders.TAGS, order.isVip() ? "VIP" : "NORMAL")
            .build();
        
        rocketMQTemplate.syncSend("order-topic", message);
    }
}
```

**消费者（短信服务）：**
```java
@Service
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "vip-sms-consumer",
    selectorExpression = "VIP"  // 只消费VIP标签的消息
)
public class VipSmsConsumer implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        // 给VIP用户发送专属优惠短信
        sendSms(order.getPhone(), "尊敬的VIP用户，您有专属优惠");
    }
}
```

---

## 项目结构

```
order-service（订单服务 - 生产者）
  ├─ OrderController
  ├─ OrderService
  └─ OrderTransactionListener

sms-service（短信服务 - 消费者）
  └─ SmsConsumer

cancel-service（取消订单服务 - 消费者）
  └─ CancelConsumer

stock-service（库存服务 - 消费者）
  └─ StockConsumer

coupon-service（优惠券服务 - 消费者）
  └─ CouponConsumer
```

---

## 核心配置

**application.yml：**
```yaml
rocketmq:
  name-server: 127.0.0.1:9876  # RocketMQ地址
  producer:
    group: order-producer       # 生产者组
    send-message-timeout: 3000  # 发送超时时间
  consumer:
    group: order-consumer       # 消费者组
```

---

## 6大功能对比

| 功能 | 场景 | 特点 | 使用方法 |
|------|------|------|----------|
| **普通消息** | 发送短信 | 异步解耦 | convertAndSend |
| **延迟消息** | 自动取消订单 | 定时执行 | syncSend + 延迟级别 |
| **事务消息** | 订单库存一致性 | 强一致性 | sendMessageInTransaction |
| **顺序消息** | 订单状态变更 | 保证顺序 | syncSendOrderly |
| **批量消息** | 批量发券 | 高吞吐 | syncSend + List |
| **消息过滤** | VIP订单 | 精准消费 | selectorExpression |

---

## 面试3句话

**RocketMQ是什么：** 阿里开源的高性能消息队列，支持100K+ QPS，适合大型高并发系统。

**核心功能：** 普通消息（异步解耦）、延迟消息（定时任务）、事务消息（强一致性）、顺序消息（保证顺序）、批量消息（高吞吐）、消息过滤（精准消费）。

**实际应用：** 订单系统用事务消息保证订单和库存一致性，延迟消息自动取消超时订单，顺序消息保证订单状态按序变更。
