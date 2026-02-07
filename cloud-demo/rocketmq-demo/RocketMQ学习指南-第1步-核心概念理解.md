# RocketMQ学习指南 - 第1步：核心概念理解

> **学习目标**：理解RocketMQ的本质、架构、6大核心功能的应用场景

---

## 一、什么是消息队列？（3分钟理解）

### 1.1 生活中的例子

**场景：你去餐厅点餐**

```
传统方式（同步）：
你点餐 → 厨师做菜 → 你等待30分钟 → 拿到菜 → 你吃饭
问题：你必须等30分钟，什么都不能干

消息队列方式（异步）：
你点餐 → 拿到号码牌（消息） → 你去玩手机
厨师看到订单（消费消息） → 做菜 → 叫号
你听到叫号 → 拿菜 → 吃饭
好处：你不用傻等，可以干别的事
```

**消息队列就是"号码牌系统"**：
- 你（生产者）：点餐，拿号码牌
- 号码牌系统（消息队列）：保存订单
- 厨师（消费者）：看订单，做菜

---

### 1.2 技术场景

**场景：用户下单买iPhone**

```java
// 传统方式（同步，慢）
public void createOrder() {
    saveOrder();           // 保存订单 - 100ms
    reduceStock();         // 扣减库存 - 200ms
    sendSMS();             // 发送短信 - 500ms
    sendEmail();           // 发送邮件 - 300ms
    updateUserLevel();     // 更新用户等级 - 150ms
    // 总耗时：1250ms，用户等1.25秒才看到"下单成功"
}

// 消息队列方式（异步，快）
public void createOrder() {
    saveOrder();           // 保存订单 - 100ms
    sendMQ("订单创建");     // 发送消息 - 10ms
    return "下单成功";      // 立即返回
    // 总耗时：110ms，用户只等0.11秒
}

// 消费者（后台慢慢处理）
@RocketMQMessageListener(topic = "order-topic")
public void onMessage(Order order) {
    reduceStock();         // 扣减库存
    sendSMS();             // 发送短信
    sendEmail();           // 发送邮件
    updateUserLevel();     // 更新用户等级
}
```

**好处**：
- 用户体验好：0.11秒 vs 1.25秒（快11倍）
- 系统解耦：订单服务不用关心短信、邮件怎么发
- 削峰填谷：双11高峰期，消息慢慢消费，不会崩溃

---

## 二、为什么选RocketMQ？（对比Kafka、RabbitMQ）

### 2.1 三大消息队列对比

| 特性 | RocketMQ | Kafka | RabbitMQ |
|------|----------|-------|----------|
| **开发语言** | Java | Scala | Erlang |
| **性能** | 10万QPS | 100万QPS | 1万QPS |
| **可靠性** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **延迟消息** | ✅ 支持 | ❌ 不支持 | ✅ 支持（插件） |
| **事务消息** | ✅ 支持 | ❌ 不支持 | ❌ 不支持 |
| **顺序消息** | ✅ 支持 | ✅ 支持 | ❌ 不支持 |
| **消息过滤** | ✅ 支持（TAG/SQL） | ❌ 不支持 | ✅ 支持 |
| **适用场景** | 电商、金融 | 日志、大数据 | 小规模业务 |

### 2.2 为什么我们选RocketMQ？

**1. 阿里出品，久经考验**
- 双11每秒处理1000万笔交易
- 支撑淘宝、天猫、支付宝

**2. 功能强大**
- 延迟消息：30分钟后自动取消订单
- 事务消息：订单+库存一致性
- 顺序消息：订单状态按序变更

**3. 运维简单**
- Java开发，容易看懂源码
- 社区活跃，文档丰富

---

## 三、RocketMQ架构（5分钟理解）

### 3.1 四大角色

```
┌─────────────────────────────────────────────────────────┐
│                      NameServer                         │
│              （注册中心，类似电话簿）                      │
│   Broker1在哪？→ 192.168.1.100:10911                    │
│   Broker2在哪？→ 192.168.1.101:10911                    │
└─────────────────────────────────────────────────────────┘
           ↑                                    ↑
           │ 注册                               │ 查询
           │                                    │
┌──────────┴──────────┐              ┌─────────┴──────────┐
│      Broker1        │              │    Producer        │
│   （消息存储）       │←─────发消息───│   （生产者）        │
│  Topic: order-topic │              │  订单服务           │
│  Queue: 4个队列     │              └────────────────────┘
└─────────┬───────────┘
          │ 拉消息
          ↓
┌─────────────────────┐
│     Consumer        │
│    （消费者）        │
│   短信服务           │
└─────────────────────┘
```

**1. NameServer（注册中心）**
- 作用：记录Broker的地址
- 类比：电话簿，查"Broker1在哪"
- 配置：`rocketmq.name-server=127.0.0.1:9876`

**2. Broker（消息存储）**
- 作用：存储消息、转发消息
- 类比：邮局，收信、发信
- 数据：存在 `D:\rocketmq\store`

**3. Producer（生产者）**
- 作用：发送消息
- 类比：寄信人
- 代码：`rocketMQTemplate.syncSend("order-topic", order)`

**4. Consumer（消费者）**
- 作用：接收消息、处理业务
- 类比：收信人
- 代码：`@RocketMQMessageListener(topic = "order-topic")`

---

### 3.2 消息发送流程（完整链路）

```
第1步：Producer启动
Producer → NameServer: "Broker在哪？"
NameServer → Producer: "Broker1在192.168.1.100:10911"

第2步：发送消息
Producer → Broker1: "发送消息：订单号ORD001"
Broker1 → 磁盘: 保存消息到 commitlog
Broker1 → Producer: "消息发送成功，MessageId=xxx"

第3步：Consumer启动
Consumer → NameServer: "Broker在哪？"
NameServer → Consumer: "Broker1在192.168.1.100:10911"
Consumer → Broker1: "订阅order-topic"

第4步：消费消息
Broker1 → Consumer: "推送消息：订单号ORD001"
Consumer → 业务逻辑: 扣减库存、发送短信
Consumer → Broker1: "消费成功"
```

---

## 四、6大核心功能详解（重点）

### 4.1 场景1：普通消息（最基础）

**业务场景**：用户下单，发送短信通知

```java
// 生产者：发送消息
@PostMapping("/send")
public Result sendNormalMessage(@RequestBody OrderDTO dto) {
    // 1. 保存订单到数据库
    Order order = orderMapper.insert(dto);
    
    // 2. 发送消息到MQ
    rocketMQTemplate.convertAndSend("order-topic", order);
    
    // 3. 立即返回（不等短信发送完成）
    return Result.success(order.getOrderNo());
}

// 消费者：接收消息
@RocketMQMessageListener(
    topic = "order-topic",           // 订阅的主题
    consumerGroup = "sms-consumer"   // 消费者组
)
public class SmsConsumer implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        // 发送短信
        smsService.send(order.getPhone(), "订单创建成功");
    }
}
```

**关键点**：
- Topic：消息主题，类似"频道"
- ConsumerGroup：消费者组，同一组内只有一个消费者消费消息
- 异步处理：订单保存后立即返回，短信慢慢发

**适用场景**：
- 发送短信、邮件
- 记录日志
- 数据同步

---

### 4.2 场景2：延迟消息（定时任务）

**业务场景**：下单后30分钟未支付，自动取消订单

```java
// 生产者：发送延迟消息
@PostMapping("/sendDelay")
public Result sendDelayMessage(@RequestBody OrderDTO dto) {
    // 1. 保存订单（status=0待支付）
    Order order = orderMapper.insert(dto);
    
    // 2. 发送延迟消息（30分钟后消费）
    Message<String> message = MessageBuilder
        .withPayload(order.getOrderNo())
        .build();
    rocketMQTemplate.syncSend("cancel-topic", message, 3000, 16);
    //                                                  ↑     ↑
    //                                              超时时间  延迟级别
    // 延迟级别16 = 30分钟
    
    return Result.success(order.getOrderNo());
}

// 消费者：30分钟后自动执行
@RocketMQMessageListener(
    topic = "cancel-topic",
    consumerGroup = "cancel-consumer"
)
public class CancelConsumer implements RocketMQListener<String> {
    @Override
    public void onMessage(String orderNo) {
        // 查询订单状态
        Order order = orderMapper.selectByOrderNo(orderNo);
        
        // 如果还是待支付，自动取消
        if (order.getStatus() == 0) {
            orderMapper.updateStatus(orderNo, -1);
            log.info("订单{}超时未支付，已自动取消", orderNo);
        }
    }
}
```

**延迟级别对照表**：
```
1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
1  2  3   4   5  6  7  8  9  10 11 12 13 14  15  16  17 18
```

**关键点**：
- 延迟级别16 = 30分钟
- 不是精确延迟，误差±1秒
- 适合订单超时、定时提醒

**适用场景**：
- 订单超时取消
- 定时推送消息
- 延迟重试

---

### 4.3 场景3：事务消息（最重要⭐）

**业务场景**：下单扣库存，保证订单和库存一致性

**问题**：如果订单保存成功，但库存扣减失败怎么办？

```java
// 错误示例（不一致）
public void createOrder() {
    orderMapper.insert(order);      // 订单保存成功
    // 这里系统崩溃了...
    stockMapper.reduce(productId);  // 库存没扣减
    // 结果：订单有了，库存没扣，超卖！
}
```

**解决方案：事务消息**

```java
// 生产者：发送事务消息
@PostMapping("/sendTransaction")
public Result sendTransactionMessage(@RequestBody OrderDTO dto) {
    // 发送事务消息
    Message<OrderDTO> message = MessageBuilder
        .withPayload(dto)
        .build();
    
    TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
        "stock-topic",
        message,
        dto  // 传递给本地事务的参数
    );
    
    return Result.success();
}

// 本地事务监听器
@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {
    
    // 第1步：执行本地事务（保存订单）
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            OrderDTO dto = (OrderDTO) arg;
            
            // 保存订单到数据库
            orderMapper.insert(dto);
            
            // 本地事务成功，提交消息
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            // 本地事务失败，回滚消息
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
    
    // 第2步：回查本地事务状态（如果第1步超时）
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 查询订单是否存在
        Order order = orderMapper.selectByOrderNo(orderNo);
        
        if (order != null) {
            return RocketMQLocalTransactionState.COMMIT;  // 订单存在，提交消息
        } else {
            return RocketMQLocalTransactionState.ROLLBACK; // 订单不存在，回滚消息
        }
    }
}

// 消费者：扣减库存
@RocketMQMessageListener(
    topic = "stock-topic",
    consumerGroup = "stock-consumer"
)
public class StockConsumer implements RocketMQListener<OrderDTO> {
    @Override
    public void onMessage(OrderDTO dto) {
        // 扣减库存（乐观锁）
        int rows = stockMapper.reduceStock(dto.getProductName(), dto.getQuantity());
        
        if (rows == 0) {
            log.error("库存不足，订单{}扣减失败", dto.getOrderNo());
            // 可以发送消息通知订单服务取消订单
        }
    }
}
```

**事务消息流程**：
```
第1步：Producer发送Half消息（半消息，对Consumer不可见）
第2步：Broker保存Half消息，返回成功
第3步：Producer执行本地事务（保存订单）
第4步：本地事务成功 → 提交消息 → Consumer可见 → 扣减库存
       本地事务失败 → 回滚消息 → Consumer不可见 → 不扣库存
第5步：如果第4步超时，Broker回查本地事务状态
```

**关键点**：
- 保证订单和库存的最终一致性
- 本地事务成功，消息才会被消费
- 本地事务失败，消息自动回滚

**适用场景**：
- 订单+库存
- 订单+支付
- 转账（A账户-100，B账户+100）

---

### 4.4 场景4：顺序消息（保证顺序）

**业务场景**：订单状态必须按顺序变更：待支付→已支付→已发货→已完成

**问题**：普通消息无法保证顺序

```
发送顺序：消息1（状态=1）→ 消息2（状态=2）→ 消息3（状态=3）
消费顺序：消息2（状态=2）→ 消息1（状态=1）→ 消息3（状态=3）
结果：订单状态乱了！
```

**解决方案：顺序消息**

```java
// 生产者：发送顺序消息
@PostMapping("/sendOrderly")
public Result sendOrderlyMessage(@RequestBody StatusDTO dto) {
    // 发送顺序消息，指定MessageSelector（订单号作为Key）
    rocketMQTemplate.syncSendOrderly(
        "order-status-topic",
        dto,
        dto.getOrderNo()  // 相同订单号的消息，发送到同一个队列
    );
    
    return Result.success();
}

// 消费者：顺序消费
@RocketMQMessageListener(
    topic = "order-status-topic",
    consumerGroup = "status-consumer",
    consumeMode = ConsumeMode.ORDERLY  // 顺序消费
)
public class StatusConsumer implements RocketMQListener<StatusDTO> {
    @Override
    public void onMessage(StatusDTO dto) {
        // 更新订单状态
        orderMapper.updateStatus(dto.getOrderNo(), dto.getStatus());
        log.info("订单{}状态变更为{}", dto.getOrderNo(), dto.getStatus());
    }
}
```

**顺序消息原理**：
```
Topic: order-status-topic
├── Queue0: 订单ORD001的消息（状态1→2→3）
├── Queue1: 订单ORD002的消息（状态1→2→3）
├── Queue2: 订单ORD003的消息（状态1→2→3）
└── Queue3: 订单ORD004的消息（状态1→2→3）

相同订单号的消息 → 发送到同一个Queue → 顺序消费
```

**关键点**：
- MessageSelector：订单号作为Key
- ConsumeMode.ORDERLY：顺序消费
- 性能下降：顺序消费比并发消费慢

**适用场景**：
- 订单状态变更
- 数据库binlog同步
- 股票交易（买入→卖出）

---

### 4.5 场景5：批量消息（提高性能）

**业务场景**：双11活动，给100万用户批量发放优惠券

**问题**：一条一条发送，太慢了

```java
// 慢（发送100万次）
for (int userId = 1; userId <= 1000000; userId++) {
    rocketMQTemplate.syncSend("coupon-topic", userId);
}
// 耗时：1000000 * 10ms = 10000秒 = 2.7小时
```

**解决方案：批量发送**

```java
// 生产者：批量发送
@PostMapping("/sendBatch")
public Result sendBatchMessage(@RequestBody BatchDTO dto) {
    List<Message<Integer>> messages = new ArrayList<>();
    
    // 构建批量消息
    for (Integer userId : dto.getUserIds()) {
        Message<Integer> message = MessageBuilder
            .withPayload(userId)
            .build();
        messages.add(message);
    }
    
    // 批量发送（一次发送10条）
    rocketMQTemplate.syncSend("coupon-topic", messages);
    
    return Result.success("批量发送 " + messages.size() + " 条消息成功");
}

// 消费者：接收消息
@RocketMQMessageListener(
    topic = "coupon-topic",
    consumerGroup = "coupon-consumer"
)
public class CouponConsumer implements RocketMQListener<Integer> {
    @Override
    public void onMessage(Integer userId) {
        // 发放优惠券
        couponMapper.insert(userId, "新用户专享券", 50.00);
    }
}
```

**批量发送规则**：
- 每批最多1000条消息
- 每批最大4MB
- 建议每批10-100条

**关键点**：
- 提高性能：批量发送比单条发送快10倍
- 注意大小：每批不超过4MB
- 失败重试：整批失败，整批重试

**适用场景**：
- 批量发券
- 批量推送
- 数据批量同步

---

### 4.6 场景6：消息过滤（TAG过滤）

**业务场景**：VIP用户下单，发送VIP专属短信；普通用户不发送

**问题**：所有订单都发送到同一个Topic，消费者收到所有消息

```java
// 问题：VIP消费者收到普通订单消息，浪费资源
@RocketMQMessageListener(topic = "order-topic")
public class VipSmsConsumer implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        if (order.getIsVip() == 1) {  // 手动过滤，浪费资源
            smsService.sendVipSms(order);
        }
    }
}
```

**解决方案：TAG过滤**

```java
// 生产者：发送带TAG的消息
@PostMapping("/sendVip")
public Result sendVipMessage(@RequestBody OrderDTO dto) {
    // 1. 保存订单（is_vip=1）
    Order order = orderMapper.insert(dto);
    
    // 2. 发送带TAG的消息
    Message<Order> message = MessageBuilder
        .withPayload(order)
        .build();
    
    rocketMQTemplate.syncSend(
        "order-topic:VIP",  // Topic:TAG
        message
    );
    
    return Result.success(order.getOrderNo());
}

// 消费者1：只消费VIP订单
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "vip-sms-consumer",
    selectorExpression = "VIP"  // 只消费TAG=VIP的消息
)
public class VipSmsConsumer implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        // 发送VIP专属短信
        smsService.sendVipSms(order);
    }
}

// 消费者2：消费所有订单
@RocketMQMessageListener(
    topic = "order-topic",
    consumerGroup = "sms-consumer",
    selectorExpression = "*"  // 消费所有TAG
)
public class SmsConsumer implements RocketMQListener<Order> {
    @Override
    public void onMessage(Order order) {
        // 发送普通短信
        smsService.sendSms(order);
    }
}
```

**TAG过滤规则**：
- `*`：消费所有TAG
- `VIP`：只消费TAG=VIP
- `VIP || SVIP`：消费TAG=VIP或SVIP

**关键点**：
- Broker端过滤：减少网络传输
- 提高性能：消费者只收到需要的消息
- 灵活订阅：不同消费者订阅不同TAG

**适用场景**：
- VIP用户特殊处理
- 不同业务类型（订单、退款、售后）
- 不同优先级（高、中、低）

---

## 五、核心概念总结

### 5.1 关键术语

| 术语 | 说明 | 类比 |
|------|------|------|
| **Topic** | 消息主题 | 频道（CCTV1、CCTV2） |
| **Tag** | 消息标签 | 节目类型（新闻、电视剧） |
| **Queue** | 消息队列 | 邮箱（每个Topic有多个Queue） |
| **Message** | 消息 | 信件 |
| **Producer** | 生产者 | 寄信人 |
| **Consumer** | 消费者 | 收信人 |
| **ConsumerGroup** | 消费者组 | 收信人团队（同一组只有一个人收信） |
| **Broker** | 消息服务器 | 邮局 |
| **NameServer** | 注册中心 | 电话簿 |

---

### 5.2 6大功能对比

| 功能 | 适用场景 | 关键特性 | 性能 |
|------|----------|----------|------|
| **普通消息** | 发短信、记日志 | 异步处理 | ⭐⭐⭐⭐⭐ |
| **延迟消息** | 订单超时取消 | 定时执行 | ⭐⭐⭐⭐ |
| **事务消息** | 订单+库存一致性 | 最终一致性 | ⭐⭐⭐ |
| **顺序消息** | 订单状态变更 | 保证顺序 | ⭐⭐ |
| **批量消息** | 批量发券 | 提高性能 | ⭐⭐⭐⭐⭐ |
| **消息过滤** | VIP用户特殊处理 | TAG过滤 | ⭐⭐⭐⭐ |

---

### 5.3 学习检查清单

**理论部分**：
- [ ] 理解消息队列的作用（异步、解耦、削峰）
- [ ] 理解RocketMQ的4大角色（NameServer、Broker、Producer、Consumer）
- [ ] 理解消息发送流程（Producer→Broker→Consumer）
- [ ] 理解6大核心功能的应用场景

**实践部分**：
- [ ] 能说出普通消息的使用场景
- [ ] 能说出延迟消息的延迟级别
- [ ] 能说出事务消息的执行流程
- [ ] 能说出顺序消息的原理
- [ ] 能说出批量消息的规则
- [ ] 能说出TAG过滤的语法

---

## 六、下一步学习

**第2步：代码深度解析**
- 逐行解析6个场景的代码
- 每个注解的作用
- 消息发送/消费的完整流程

**准备好了吗？** 告诉我，我继续创建第2步文档。

---

**学习时间**：30分钟  
**难度**：⭐⭐（简单）  
**掌握标准**：能用自己的话解释6大功能的应用场景
