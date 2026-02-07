# RocketMQ学习指南 - 第2步：代码深度解析

> **学习目标**：逐行理解6个场景的代码实现，掌握RocketMQ的核心API

---

## 一、学习方法

### 1.1 代码阅读顺序

```
第1步：看业务流程图（理解业务逻辑）
第2步：看生产者代码（如何发送消息）
第3步：看消费者代码（如何消费消息）
第4步：看数据库变化（验证结果）
```

### 1.2 重点关注

- **注解**：每个注解的作用是什么？
- **API**：RocketMQTemplate的方法有什么区别？
- **参数**：每个参数的含义是什么？
- **流程**：消息从发送到消费的完整链路

---

## 二、场景1：普通消息 - 订单创建 + 短信通知

### 2.1 业务流程图

```
用户下单
    ↓
保存订单到数据库（status=0待支付）
    ↓
发送消息到MQ（order-topic）
    ↓
立即返回"下单成功"
    ↓
消费者收到消息
    ↓
发送短信通知用户
    ↓
保存短信记录到数据库
```

### 2.2 生产者代码（逐行解析）

**文件位置**：`rocketmq-producer/src/main/java/cn/itcast/rocketmq/producer/service/MessageService.java`

```java
/**
 * 场景1：普通消息 - 发送短信通知
 */
public String sendNormalMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
    // 第1步：生成订单号
    // 格式：ORD + 年月日时分秒 + 4位随机数
    // 例如：ORD202602071430001234
    String orderNo = generateOrderNo();
```

    // 第2步：创建订单对象
    Order order = new Order();
    order.setOrderNo(orderNo);           // 订单号
    order.setUserId(userId);             // 用户ID
    order.setProductName(productName);   // 商品名称
    order.setPrice(price);               // 商品价格
    order.setQuantity(quantity);         // 购买数量
    order.setStatus(0);                  // 订单状态：0=待支付
    order.setIsVip(0);                   // 是否VIP：0=普通用户

    // 第3步：保存订单到数据库
    // SQL：INSERT INTO tb_order (...) VALUES (...)
    orderMapper.insert(order);
    log.info("订单创建成功：{}", orderNo);

    // 第4步：发送普通消息到MQ ⭐核心代码⭐
    // convertAndSend = 转换并发送
    // 参数1：destination = "order-topic"（消息主题）
    // 参数2：payload = order（消息内容，自动转JSON）
    rocketMQTemplate.convertAndSend("order-topic", order);
    log.info("普通消息发送成功：{}", orderNo);

    // 第5步：立即返回订单号（不等短信发送完成）
    return orderNo;
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `convertAndSend` | 同步发送消息，等待Broker返回成功 |
| `order-topic` | Topic名称，消费者通过Topic订阅消息 |
| `order` | 消息体，自动序列化为JSON |
| 异步处理 | 订单保存后立即返回，短信异步发送 |


### 2.3 消费者代码（逐行解析）

**文件位置**：`rocketmq-consumer/src/main/java/cn/itcast/rocketmq/consumer/listener/SmsConsumer.java`

```java
/**
 * 短信消费者 - 接收订单消息，发送短信通知
 */
@Slf4j
@Service
// ⭐核心注解⭐：标记这是一个RocketMQ消息监听器
@RocketMQMessageListener(
    topic = "order-topic",              // 订阅的主题（必须和生产者一致）
    consumerGroup = "sms-consumer",     // 消费者组（同一组内只有一个消费者消费消息）
    selectorExpression = "*"            // 消息过滤表达式（*表示消费所有TAG）
)
public class SmsConsumer implements RocketMQListener<Order> {

    @Autowired
    private SmsLogMapper smsLogMapper;

    /**
     * 消费消息的核心方法
     * @param order 消息内容（自动反序列化为Order对象）
     */
    @Override
    public void onMessage(Order order) {
        log.info("========== 普通消息消费 ==========");
        log.info("收到订单消息：订单号={}, 商品={}, 价格={}, 数量={}",
                order.getOrderNo(), order.getProductName(), order.getPrice(), order.getQuantity());

        // 第1步：模拟发送短信
        // 实际项目中，这里调用短信平台API
        String phone = "138****" + order.getUserId();
        String content = String.format("您的订单%s已创建成功，商品：%s，金额：%.2f元",
                order.getOrderNo(), order.getProductName(), order.getPrice());

        log.info("短信发送成功：手机号={}, 内容={}", phone, content);

        // 第2步：保存短信记录到数据库
        SmsLog smsLog = new SmsLog();
        smsLog.setOrderNo(order.getOrderNo());
        smsLog.setPhone(phone);
        smsLog.setContent(content);
        smsLog.setStatus(1);  // 1=发送成功
        smsLogMapper.insert(smsLog);

        log.info("==================================");
    }
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `@RocketMQMessageListener` | 标记消息监听器，自动订阅Topic |
| `topic` | 订阅的主题，必须和生产者一致 |
| `consumerGroup` | 消费者组，同一组内只有一个消费者消费消息 |
| `selectorExpression` | 消息过滤表达式，`*`表示消费所有TAG |
| `onMessage` | 消费消息的核心方法，自动反序列化 |
| 自动ACK | 方法执行成功，自动确认消息；抛异常，自动重试 |


### 2.4 完整流程图

```
┌─────────────┐
│   用户下单   │
└──────┬──────┘
       │
       ↓
┌─────────────────────────────────────┐
│  生产者（MessageService）            │
│  1. generateOrderNo()               │
│  2. orderMapper.insert(order)       │
│  3. rocketMQTemplate.convertAndSend │
│  4. return orderNo                  │
└──────┬──────────────────────────────┘
       │ 发送消息
       ↓
┌─────────────────────────────────────┐
│  RocketMQ Broker                    │
│  - 保存消息到commitlog              │
│  - 返回MessageId                    │
└──────┬──────────────────────────────┘
       │ 推送消息
       ↓
┌─────────────────────────────────────┐
│  消费者（SmsConsumer）               │
│  1. onMessage(order)                │
│  2. 发送短信                         │
│  3. smsLogMapper.insert(smsLog)     │
│  4. 返回成功（自动ACK）              │
└─────────────────────────────────────┘
```

### 2.5 核心要点总结

| 要点 | 说明 |
|------|------|
| **同步发送** | `convertAndSend`会等待Broker返回成功 |
| **异步处理** | 订单保存后立即返回，短信异步发送 |
| **自动序列化** | Order对象自动转JSON，消费者自动反序列化 |
| **自动ACK** | 消费成功自动确认，失败自动重试 |
| **解耦** | 订单服务不依赖短信服务，降低耦合 |

---

## 三、场景2：延迟消息 - 30分钟后自动取消订单

### 3.1 业务流程图

```
用户下单
    ↓
保存订单到数据库（status=0待支付）
    ↓
发送延迟消息到MQ（30分钟后消费）
    ↓
立即返回"下单成功"
    ↓
用户30分钟内未支付
    ↓
30分钟后，消费者收到消息
    ↓
查询订单状态
    ↓
如果还是待支付，自动取消订单（status=-1）
```

### 3.2 生产者代码（逐行解析）

```java
/**
 * 场景2：延迟消息 - 自动取消超时订单
 */
public String sendDelayMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
    // 第1步：生成订单号
    String orderNo = generateOrderNo();

    // 第2步：创建订单对象
    Order order = new Order();
    order.setOrderNo(orderNo);
    order.setUserId(userId);
    order.setProductName(productName);
    order.setPrice(price);
    order.setQuantity(quantity);
    order.setStatus(0);  // 订单状态：0=待支付
    order.setIsVip(0);

    // 第3步：保存订单到数据库
    orderMapper.insert(order);
    log.info("订单创建成功（待支付）：{}", orderNo);

    // 第4步：构建延迟消息 ⭐核心代码⭐
    // MessageBuilder：消息构建器
    // withPayload：设置消息内容（只发送订单号，减少消息大小）
    Message<String> message = MessageBuilder
            .withPayload(orderNo)
            .build();

    // 第5步：发送延迟消息
    // 参数1：destination = "cancel-topic"（取消订单主题）
    // 参数2：message = 消息对象
    // 参数3：timeout = 3000ms（发送超时时间）
    // 参数4：delayLevel = 16（延迟级别，16=30分钟）⭐重点⭐
    SendResult result = rocketMQTemplate.syncSend("cancel-topic", message, 3000, 16);
    log.info("延迟消息发送成功：{}，30分钟后自动取消", orderNo);

    return orderNo;
}
```

**延迟级别对照表**：

```
级别：  1   2   3    4    5   6   7   8   9   10  11  12  13  14   15   16   17  18
延迟： 1s  5s  10s  30s  1m  2m  3m  4m  5m  6m  7m  8m  9m  10m  20m  30m  1h  2h
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `MessageBuilder` | 消息构建器，用于构建复杂消息 |
| `withPayload` | 设置消息内容 |
| `syncSend` | 同步发送消息，支持超时和延迟级别 |
| `delayLevel` | 延迟级别，16=30分钟 |
| 只发订单号 | 减少消息大小，消费时再查数据库 |


### 3.3 消费者代码（逐行解析）

**文件位置**：`rocketmq-consumer/src/main/java/cn/itcast/rocketmq/consumer/listener/CancelConsumer.java`

```java
/**
 * 订单取消消费者 - 30分钟后自动取消超时订单
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "cancel-topic",             // 订阅取消订单主题
    consumerGroup = "cancel-consumer"   // 消费者组
)
public class CancelConsumer implements RocketMQListener<String> {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 30分钟后自动执行
     * @param orderNo 订单号
     */
    @Override
    public void onMessage(String orderNo) {
        log.info("========== 延迟消息消费（30分钟后） ==========");
        log.info("检查订单是否超时：订单号={}", orderNo);

        // 第1步：查询订单状态
        Order order = orderMapper.selectByOrderNo(orderNo);

        if (order == null) {
            log.warn("订单不存在：{}", orderNo);
            return;
        }

        // 第2步：判断订单状态
        if (order.getStatus() == 0) {
            // 状态=0（待支付），说明用户30分钟内未支付
            // 自动取消订单
            orderMapper.updateStatus(orderNo, -1);  // -1=已取消
            log.info("订单{}超时未支付，已自动取消", orderNo);
        } else {
            // 状态!=0，说明用户已支付或已取消
            log.info("订单{}状态={}, 无需取消", orderNo, order.getStatus());
        }

        log.info("===========================================");
    }
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| 延迟消费 | 消息发送后30分钟才被消费 |
| 状态判断 | 消费时再查数据库，判断是否需要取消 |
| 幂等性 | 如果用户已支付，不会重复取消 |
| 误差 | 延迟消息不是精确延迟，误差±1秒 |

### 3.4 完整流程图

```
┌─────────────┐
│   用户下单   │
└──────┬──────┘
       │
       ↓
┌─────────────────────────────────────┐
│  生产者（MessageService）            │
│  1. orderMapper.insert(order)       │
│     status=0（待支付）               │
│  2. rocketMQTemplate.syncSend       │
│     delayLevel=16（30分钟）         │
└──────┬──────────────────────────────┘
       │ 发送延迟消息
       ↓
┌─────────────────────────────────────┐
│  RocketMQ Broker                    │
│  - 保存消息到延迟队列                │
│  - 30分钟后投递到消费队列            │
└──────┬──────────────────────────────┘
       │ 30分钟后推送消息
       ↓
┌─────────────────────────────────────┐
│  消费者（CancelConsumer）            │
│  1. orderMapper.selectByOrderNo     │
│  2. if (status == 0)                │
│  3.   orderMapper.updateStatus(-1)  │
│  4. 返回成功                         │
└─────────────────────────────────────┘
```

### 3.5 核心要点总结

| 要点 | 说明 |
|------|------|
| **延迟级别** | 16=30分钟，不支持自定义延迟时间 |
| **状态判断** | 消费时再查数据库，避免重复取消 |
| **幂等性** | 如果用户已支付，不会重复取消 |
| **误差** | 延迟消息不是精确延迟，误差±1秒 |
| **适用场景** | 订单超时、定时提醒、延迟重试 |

---

## 四、场景3：事务消息 - 订单和库存一致性

### 4.1 业务流程图

```
用户下单
    ↓
发送Half消息（半消息，对Consumer不可见）
    ↓
Broker保存Half消息，返回成功
    ↓
执行本地事务（保存订单到数据库）
    ↓
本地事务成功？
    ├─ 是 → 提交消息（Commit）→ Consumer可见 → 扣减库存
    └─ 否 → 回滚消息（Rollback）→ Consumer不可见 → 不扣库存
    ↓
如果超时，Broker回查本地事务状态
    ├─ 订单存在 → 提交消息
    └─ 订单不存在 → 回滚消息
```

### 4.2 生产者代码（逐行解析）

```java
/**
 * 场景3：事务消息 - 订单和库存一致性
 */
public String sendTransactionMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
    // 第1步：生成订单号
    String orderNo = generateOrderNo();

    // 第2步：创建订单对象
    Order order = new Order();
    order.setOrderNo(orderNo);
    order.setUserId(userId);
    order.setProductName(productName);
    order.setPrice(price);
    order.setQuantity(quantity);
    order.setStatus(0);
    order.setIsVip(0);

    // 第3步：构建事务消息
    Message<Order> message = MessageBuilder
            .withPayload(order)
            .build();

    // 第4步：发送事务消息 ⭐核心代码⭐
    // sendMessageInTransaction：发送事务消息
    // 参数1：destination = "stock-topic"（库存主题）
    // 参数2：message = 消息对象
    // 参数3：arg = 传递给本地事务的参数（order对象）
    rocketMQTemplate.sendMessageInTransaction("stock-topic", message, order);
    log.info("事务消息发送成功：{}", orderNo);

    return orderNo;
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `sendMessageInTransaction` | 发送事务消息，触发本地事务 |
| `arg` | 传递给本地事务的参数 |
| Half消息 | 消息对Consumer不可见，等待提交或回滚 |


### 4.3 本地事务监听器（逐行解析）

**文件位置**：`rocketmq-producer/src/main/java/cn/itcast/rocketmq/producer/listener/OrderTransactionListener.java`

```java
/**
 * 订单事务监听器 - 处理本地事务和回查
 */
@Slf4j
@Component
// ⭐核心注解⭐：标记这是一个事务消息监听器
@RocketMQTransactionListener
public class OrderTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 第1步：执行本地事务（保存订单）
     * 这个方法在发送Half消息成功后立即执行
     * 
     * @param msg 消息对象
     * @param arg 发送消息时传递的参数（order对象）
     * @return 本地事务状态
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        log.info("========== 执行本地事务 ==========");
        
        try {
            // 第1步：获取订单对象
            Order order = (Order) arg;
            log.info("开始保存订单：{}", order.getOrderNo());

            // 第2步：保存订单到数据库
            orderMapper.insert(order);
            log.info("订单保存成功：{}", order.getOrderNo());

            // 第3步：本地事务成功，提交消息
            // COMMIT：提交消息，Consumer可以消费
            log.info("本地事务成功，提交消息");
            return RocketMQLocalTransactionState.COMMIT;

        } catch (Exception e) {
            // 第4步：本地事务失败，回滚消息
            // ROLLBACK：回滚消息，Consumer不可见，消息被删除
            log.error("本地事务失败，回滚消息：{}", e.getMessage());
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 第2步：回查本地事务状态
     * 如果executeLocalTransaction超时或返回UNKNOWN，Broker会回查
     * 
     * @param msg 消息对象
     * @return 本地事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        log.info("========== 回查本地事务状态 ==========");

        try {
            // 第1步：从消息中获取订单号
            String payload = new String((byte[]) msg.getPayload());
            Order order = JSON.parseObject(payload, Order.class);
            String orderNo = order.getOrderNo();
            log.info("回查订单：{}", orderNo);

            // 第2步：查询订单是否存在
            Order dbOrder = orderMapper.selectByOrderNo(orderNo);

            if (dbOrder != null) {
                // 订单存在，说明本地事务成功，提交消息
                log.info("订单存在，提交消息");
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                // 订单不存在，说明本地事务失败，回滚消息
                log.info("订单不存在，回滚消息");
                return RocketMQLocalTransactionState.ROLLBACK;
            }

        } catch (Exception e) {
            // 回查失败，返回UNKNOWN，Broker会继续回查
            log.error("回查失败：{}", e.getMessage());
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `@RocketMQTransactionListener` | 标记事务监听器 |
| `executeLocalTransaction` | 执行本地事务，返回COMMIT/ROLLBACK/UNKNOWN |
| `checkLocalTransaction` | 回查本地事务状态，Broker定时回查 |
| `COMMIT` | 提交消息，Consumer可以消费 |
| `ROLLBACK` | 回滚消息，Consumer不可见 |
| `UNKNOWN` | 未知状态，Broker继续回查 |


### 4.4 消费者代码（逐行解析）

**文件位置**：`rocketmq-consumer/src/main/java/cn/itcast/rocketmq/consumer/listener/StockConsumer.java`

```java
/**
 * 库存消费者 - 扣减库存
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "stock-topic",              // 订阅库存主题
    consumerGroup = "stock-consumer"    // 消费者组
)
public class StockConsumer implements RocketMQListener<Order> {

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private StockLogMapper stockLogMapper;

    /**
     * 消费消息：扣减库存
     * 只有本地事务提交后，这个方法才会被调用
     */
    @Override
    public void onMessage(Order order) {
        log.info("========== 事务消息消费 ==========");
        log.info("收到订单消息：订单号={}, 商品={}, 数量={}",
                order.getOrderNo(), order.getProductName(), order.getQuantity());

        // 第1步：扣减库存（乐观锁）
        // UPDATE tb_stock SET stock = stock - #{quantity}, version = version + 1
        // WHERE product_name = #{productName} AND stock >= #{quantity} AND version = #{version}
        int rows = stockMapper.reduceStock(order.getProductName(), order.getQuantity());

        if (rows > 0) {
            // 扣减成功
            log.info("库存扣减成功：商品={}, 数量={}", order.getProductName(), order.getQuantity());

            // 第2步：记录库存扣减日志
            StockLog stockLog = new StockLog();
            stockLog.setOrderNo(order.getOrderNo());
            stockLog.setProductName(order.getProductName());
            stockLog.setQuantity(order.getQuantity());
            stockLog.setType(1);  // 1=扣减
            stockLogMapper.insert(stockLog);

        } else {
            // 扣减失败（库存不足）
            log.error("库存不足，订单{}扣减失败", order.getOrderNo());
            // 实际项目中，这里可以发送消息通知订单服务取消订单
        }

        log.info("==================================");
    }
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| 乐观锁 | `WHERE stock >= #{quantity} AND version = #{version}` |
| 库存不足 | 扣减失败时，可以通知订单服务取消订单 |
| 最终一致性 | 订单和库存最终一致，不是强一致 |
| 消费时机 | 只有本地事务提交后，消费者才会收到消息 |

### 4.5 完整流程图

```
┌─────────────┐
│   用户下单   │
└──────┬──────┘
       │
       ↓
┌─────────────────────────────────────────────┐
│  生产者（MessageService）                    │
│  1. sendMessageInTransaction                │
└──────┬──────────────────────────────────────┘
       │ 发送Half消息
       ↓
┌─────────────────────────────────────────────┐
│  RocketMQ Broker                            │
│  - 保存Half消息（对Consumer不可见）          │
│  - 返回成功                                  │
└──────┬──────────────────────────────────────┘
       │ 触发本地事务
       ↓
┌─────────────────────────────────────────────┐
│  本地事务监听器（OrderTransactionListener）  │
│  1. executeLocalTransaction                 │
│  2. orderMapper.insert(order)               │
│  3. return COMMIT/ROLLBACK                  │
└──────┬──────────────────────────────────────┘
       │ 返回COMMIT
       ↓
┌─────────────────────────────────────────────┐
│  RocketMQ Broker                            │
│  - 提交消息（对Consumer可见）                │
└──────┬──────────────────────────────────────┘
       │ 推送消息
       ↓
┌─────────────────────────────────────────────┐
│  消费者（StockConsumer）                     │
│  1. onMessage(order)                        │
│  2. stockMapper.reduceStock                 │
│  3. stockLogMapper.insert                   │
└─────────────────────────────────────────────┘
```

### 4.6 核心要点总结

| 要点 | 说明 |
|------|------|
| **Half消息** | 对Consumer不可见，等待提交或回滚 |
| **本地事务** | 保存订单到数据库 |
| **提交消息** | 本地事务成功，提交消息，Consumer可见 |
| **回滚消息** | 本地事务失败，回滚消息，Consumer不可见 |
| **回查机制** | 如果超时，Broker回查本地事务状态 |
| **最终一致性** | 订单和库存最终一致，不是强一致 |

---

## 五、场景4：顺序消息 - 订单状态变更

### 5.1 业务流程图

```
订单创建（status=0）
    ↓
发送消息：status=1（已支付）
    ↓
发送消息：status=2（已发货）
    ↓
发送消息：status=3（已完成）
    ↓
消费者按顺序消费：0→1→2→3
```

### 5.2 生产者代码（逐行解析）

```java
/**
 * 场景4：顺序消息 - 订单状态变更
 */
public String sendOrderlyMessage(String orderNo, Integer status) {
    // 构建消息内容：订单号:状态
    String message = orderNo + ":" + status;

    // 发送顺序消息 ⭐核心代码⭐
    // syncSendOrderly：同步发送顺序消息
    // 参数1：destination = "order-status-topic"（订单状态主题）
    // 参数2：payload = message（消息内容）
    // 参数3：hashKey = orderNo（哈希键，相同订单号发到同一个队列）⭐重点⭐
    rocketMQTemplate.syncSendOrderly("order-status-topic", message, orderNo);
    log.info("顺序消息发送成功：订单号={}, 状态={}", orderNo, status);

    return "顺序消息发送成功";
}
```

**顺序消息原理**：

```
Topic: order-status-topic（4个队列）
├── Queue0: 订单ORD001的消息（status: 1→2→3）
├── Queue1: 订单ORD002的消息（status: 1→2→3）
├── Queue2: 订单ORD003的消息（status: 1→2→3）
└── Queue3: 订单ORD004的消息（status: 1→2→3）

哈希算法：hash(orderNo) % 4 = queueId
相同订单号 → 相同队列 → 顺序消费
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `syncSendOrderly` | 同步发送顺序消息 |
| `hashKey` | 哈希键，相同Key发到同一个队列 |
| 队列数量 | Topic默认4个队列，可配置 |
| 顺序保证 | 同一队列内的消息顺序消费 |


### 5.3 消费者代码（逐行解析）

**文件位置**：`rocketmq-consumer/src/main/java/cn/itcast/rocketmq/consumer/listener/StatusConsumer.java`

```java
/**
 * 订单状态消费者 - 顺序消费订单状态变更
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "order-status-topic",           // 订阅订单状态主题
    consumerGroup = "status-consumer",      // 消费者组
    consumeMode = ConsumeMode.ORDERLY       // ⭐顺序消费模式⭐
)
public class StatusConsumer implements RocketMQListener<String> {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 顺序消费消息
     * 同一个队列的消息，按顺序消费
     */
    @Override
    public void onMessage(String message) {
        log.info("========== 顺序消息消费 ==========");

        // 第1步：解析消息内容
        // 格式：订单号:状态
        String[] parts = message.split(":");
        String orderNo = parts[0];
        Integer status = Integer.parseInt(parts[1]);

        log.info("收到订单状态变更：订单号={}, 状态={}", orderNo, status);

        // 第2步：更新订单状态
        orderMapper.updateStatus(orderNo, status);
        log.info("订单状态更新成功：订单号={}, 状态={}", orderNo, status);

        log.info("==================================");
    }
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `ConsumeMode.ORDERLY` | 顺序消费模式 |
| `ConsumeMode.CONCURRENTLY` | 并发消费模式（默认） |
| 顺序保证 | 同一队列内的消息按顺序消费 |
| 性能下降 | 顺序消费比并发消费慢 |

### 5.4 完整流程图

```
┌─────────────────────────────────────┐
│  生产者发送3条消息（订单ORD001）     │
│  1. syncSendOrderly("...", "ORD001:1", "ORD001")  │
│  2. syncSendOrderly("...", "ORD001:2", "ORD001")  │
│  3. syncSendOrderly("...", "ORD001:3", "ORD001")  │
└──────┬──────────────────────────────┘
       │ hash(ORD001) % 4 = 1
       ↓
┌─────────────────────────────────────┐
│  RocketMQ Broker                    │
│  Queue1: [ORD001:1, ORD001:2, ORD001:3]  │
└──────┬──────────────────────────────┘
       │ 按顺序推送
       ↓
┌─────────────────────────────────────┐
│  消费者（StatusConsumer）            │
│  1. onMessage("ORD001:1")           │
│  2. onMessage("ORD001:2")           │
│  3. onMessage("ORD001:3")           │
└─────────────────────────────────────┘
```

### 5.5 核心要点总结

| 要点 | 说明 |
|------|------|
| **哈希键** | 相同Key发到同一个队列 |
| **顺序消费** | `ConsumeMode.ORDERLY` |
| **性能下降** | 顺序消费比并发消费慢 |
| **适用场景** | 订单状态变更、数据库binlog同步 |

---

## 六、场景5：批量消息 - 批量发送优惠券

### 6.1 业务流程图

```
双11活动开始
    ↓
批量发送10万条消息（用户ID）
    ↓
消费者接收消息
    ↓
发放优惠券到用户账户
```

### 6.2 生产者代码（逐行解析）

```java
/**
 * 场景5：批量消息 - 批量发送优惠券
 */
public String sendBatchMessage(List<Long> userIds) {
    int totalSent = 0;
    
    // 循环发送消息
    for (Long userId : userIds) {
        // 构建消息
        Message<Long> message = MessageBuilder.withPayload(userId).build();
        
        // 发送消息
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
```

**批量发送规则**：

| 规则 | 说明 |
|------|------|
| 每批最多1000条 | 超过1000条需要分批 |
| 每批最大4MB | 超过4MB需要分批 |
| 建议每批10-100条 | 平衡性能和可靠性 |
| 失败重试 | 整批失败，整批重试 |

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| 循环发送 | 逐条发送，简单可靠 |
| 性能提升 | 比单条发送快10倍 |
| 注意大小 | 每批不超过4MB |

### 6.3 消费者代码（逐行解析）

**文件位置**：`rocketmq-consumer/src/main/java/cn/itcast/rocketmq/consumer/listener/CouponConsumer.java`

```java
/**
 * 优惠券消费者 - 发放优惠券
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "coupon-topic",             // 订阅优惠券主题
    consumerGroup = "coupon-consumer"   // 消费者组
)
public class CouponConsumer implements RocketMQListener<Long> {

    @Autowired
    private CouponMapper couponMapper;

    /**
     * 消费消息：发放优惠券
     */
    @Override
    public void onMessage(Long userId) {
        // 第1步：发放优惠券
        Coupon coupon = new Coupon();
        coupon.setUserId(userId);
        coupon.setCouponName("新用户专享券");
        coupon.setAmount(new BigDecimal("50.00"));
        coupon.setStatus(0);  // 0=未使用

        couponMapper.insert(coupon);
        log.info("优惠券发放成功：用户ID={}, 金额=50元", userId);
    }
}
```

### 6.4 核心要点总结

| 要点 | 说明 |
|------|------|
| **批量发送** | 提高性能，比单条发送快10倍 |
| **注意大小** | 每批不超过4MB |
| **失败重试** | 整批失败，整批重试 |
| **适用场景** | 批量发券、批量推送、数据批量同步 |

---

## 七、场景6：消息过滤 - 只处理VIP订单

### 7.1 业务流程图

```
用户下单（VIP用户）
    ↓
保存订单到数据库（is_vip=1）
    ↓
发送消息到MQ（TAG=VIP）
    ↓
VIP消费者收到消息（只消费TAG=VIP）
    ↓
发送VIP专属短信
```

### 7.2 生产者代码（逐行解析）

```java
/**
 * 场景6：消息过滤 - 只处理VIP订单
 */
public String sendVipMessage(Long userId, String productName, BigDecimal price, Integer quantity) {
    // 第1步：生成订单号
    String orderNo = generateOrderNo();

    // 第2步：创建订单对象
    Order order = new Order();
    order.setOrderNo(orderNo);
    order.setUserId(userId);
    order.setProductName(productName);
    order.setPrice(price);
    order.setQuantity(quantity);
    order.setStatus(0);
    order.setIsVip(1);  // ⭐VIP用户⭐

    // 第3步：保存订单到数据库
    orderMapper.insert(order);
    log.info("VIP订单创建成功：{}", orderNo);

    // 第4步：发送消息时打标签 ⭐核心代码⭐
    Message<Order> message = MessageBuilder
            .withPayload(order)
            .setHeader(RocketMQHeaders.TAGS, "VIP")  // ⭐设置TAG=VIP⭐
            .build();

    rocketMQTemplate.syncSend("order-topic", message);
    log.info("VIP消息发送成功：{}", orderNo);

    return orderNo;
}
```

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `RocketMQHeaders.TAGS` | 设置消息TAG |
| `"VIP"` | TAG值，可以是任意字符串 |
| Broker端过滤 | 减少网络传输 |


### 7.3 消费者代码（逐行解析）

**文件位置**：`rocketmq-consumer/src/main/java/cn/itcast/rocketmq/consumer/listener/VipSmsConsumer.java`

```java
/**
 * VIP短信消费者 - 只处理VIP订单
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "order-topic",              // 订阅订单主题
    consumerGroup = "vip-sms-consumer", // 消费者组
    selectorExpression = "VIP"          // ⭐只消费TAG=VIP的消息⭐
)
public class VipSmsConsumer implements RocketMQListener<Order> {

    @Autowired
    private SmsLogMapper smsLogMapper;

    /**
     * 消费VIP订单消息
     * 只有TAG=VIP的消息才会被消费
     */
    @Override
    public void onMessage(Order order) {
        log.info("========== VIP消息消费 ==========");
        log.info("收到VIP订单消息：订单号={}, 商品={}, 价格={}",
                order.getOrderNo(), order.getProductName(), order.getPrice());

        // 第1步：发送VIP专属短信
        String phone = "138****" + order.getUserId();
        String content = String.format("尊敬的VIP用户，您的订单%s已创建成功，专属客服将为您服务",
                order.getOrderNo());

        log.info("VIP短信发送成功：手机号={}, 内容={}", phone, content);

        // 第2步：保存短信记录
        SmsLog smsLog = new SmsLog();
        smsLog.setOrderNo(order.getOrderNo());
        smsLog.setPhone(phone);
        smsLog.setContent(content);
        smsLog.setStatus(1);
        smsLogMapper.insert(smsLog);

        log.info("==================================");
    }
}
```

**TAG过滤规则**：

| 表达式 | 说明 |
|--------|------|
| `*` | 消费所有TAG |
| `VIP` | 只消费TAG=VIP |
| `VIP || SVIP` | 消费TAG=VIP或SVIP |
| `VIP && ACTIVE` | 不支持AND操作 |

**关键知识点**：

| 知识点 | 说明 |
|--------|------|
| `selectorExpression` | TAG过滤表达式 |
| Broker端过滤 | 减少网络传输 |
| 提高性能 | 消费者只收到需要的消息 |
| 灵活订阅 | 不同消费者订阅不同TAG |

### 7.4 完整流程图

```
┌─────────────────────────────────────┐
│  生产者（MessageService）            │
│  1. order.setIsVip(1)               │
│  2. setHeader(TAGS, "VIP")          │
│  3. syncSend("order-topic", message)│
└──────┬──────────────────────────────┘
       │ 发送消息（TAG=VIP）
       ↓
┌─────────────────────────────────────┐
│  RocketMQ Broker                    │
│  - 保存消息（TAG=VIP）               │
│  - 过滤消息（只推送给订阅VIP的消费者）│
└──────┬──────────────────────────────┘
       │ 推送消息（TAG=VIP）
       ↓
┌─────────────────────────────────────┐
│  VIP消费者（VipSmsConsumer）         │
│  selectorExpression = "VIP"         │
│  1. onMessage(order)                │
│  2. 发送VIP专属短信                  │
└─────────────────────────────────────┘
```

### 7.5 核心要点总结

| 要点 | 说明 |
|------|------|
| **TAG过滤** | Broker端过滤，减少网络传输 |
| **灵活订阅** | 不同消费者订阅不同TAG |
| **提高性能** | 消费者只收到需要的消息 |
| **适用场景** | VIP用户、不同业务类型、不同优先级 |

---

## 八、核心API总结

### 8.1 生产者API

| API | 说明 | 适用场景 |
|-----|------|----------|
| `convertAndSend(topic, payload)` | 同步发送普通消息 | 普通消息 |
| `syncSend(topic, message, timeout, delayLevel)` | 同步发送延迟消息 | 延迟消息 |
| `sendMessageInTransaction(topic, message, arg)` | 发送事务消息 | 事务消息 |
| `syncSendOrderly(topic, payload, hashKey)` | 同步发送顺序消息 | 顺序消息 |
| `syncSend(topic, message)` | 同步发送消息（带TAG） | 消息过滤 |

### 8.2 消费者注解

| 注解参数 | 说明 | 示例 |
|---------|------|------|
| `topic` | 订阅的主题 | `"order-topic"` |
| `consumerGroup` | 消费者组 | `"sms-consumer"` |
| `selectorExpression` | TAG过滤表达式 | `"VIP"` 或 `"*"` |
| `consumeMode` | 消费模式 | `ORDERLY` 或 `CONCURRENTLY` |

### 8.3 消息构建器

```java
// 基础消息
Message<String> message = MessageBuilder
    .withPayload("消息内容")
    .build();

// 带TAG的消息
Message<Order> message = MessageBuilder
    .withPayload(order)
    .setHeader(RocketMQHeaders.TAGS, "VIP")
    .build();

// 带自定义属性的消息
Message<Order> message = MessageBuilder
    .withPayload(order)
    .setHeader("userId", 1001)
    .setHeader("source", "APP")
    .build();
```

---

## 九、学习检查清单

### 9.1 场景1：普通消息
- [ ] 理解`convertAndSend`的作用
- [ ] 理解`@RocketMQMessageListener`注解
- [ ] 理解`onMessage`方法的执行时机
- [ ] 理解自动序列化和反序列化

### 9.2 场景2：延迟消息
- [ ] 理解延迟级别对照表
- [ ] 理解`syncSend`的4个参数
- [ ] 理解延迟消息的误差范围
- [ ] 理解状态判断的重要性

### 9.3 场景3：事务消息
- [ ] 理解Half消息的概念
- [ ] 理解`executeLocalTransaction`的执行时机
- [ ] 理解`checkLocalTransaction`的回查机制
- [ ] 理解COMMIT/ROLLBACK/UNKNOWN的区别

### 9.4 场景4：顺序消息
- [ ] 理解`syncSendOrderly`的3个参数
- [ ] 理解hashKey的作用
- [ ] 理解`ConsumeMode.ORDERLY`的含义
- [ ] 理解顺序消费的性能影响

### 9.5 场景5：批量消息
- [ ] 理解批量发送的规则
- [ ] 理解每批最多1000条的限制
- [ ] 理解每批最大4MB的限制
- [ ] 理解失败重试机制

### 9.6 场景6：消息过滤
- [ ] 理解`RocketMQHeaders.TAGS`的作用
- [ ] 理解`selectorExpression`的语法
- [ ] 理解Broker端过滤的优势
- [ ] 理解TAG过滤的适用场景

---

## 十、常见问题

### 10.1 消息发送失败怎么办？

```java
try {
    rocketMQTemplate.convertAndSend("order-topic", order);
} catch (Exception e) {
    log.error("消息发送失败：{}", e.getMessage());
    // 1. 记录日志
    // 2. 保存到数据库，定时重试
    // 3. 告警通知
}
```

### 10.2 消费失败怎么办？

```java
@Override
public void onMessage(Order order) {
    try {
        // 业务逻辑
        smsService.send(order);
    } catch (Exception e) {
        log.error("消费失败：{}", e.getMessage());
        // 抛出异常，RocketMQ会自动重试
        throw new RuntimeException("消费失败", e);
    }
}
```

### 10.3 如何保证消息不丢失？

1. **生产者**：使用同步发送，等待Broker返回成功
2. **Broker**：消息持久化到磁盘
3. **消费者**：消费成功后才ACK，失败自动重试

### 10.4 如何保证消息不重复？

1. **幂等性**：消费者做幂等处理
2. **唯一键**：使用订单号等唯一键去重
3. **数据库约束**：使用唯一索引防止重复插入

```java
@Override
public void onMessage(Order order) {
    // 幂等性处理：先查询是否已处理
    SmsLog existLog = smsLogMapper.selectByOrderNo(order.getOrderNo());
    if (existLog != null) {
        log.info("消息已处理，跳过：{}", order.getOrderNo());
        return;
    }
    
    // 业务逻辑
    smsService.send(order);
}
```

---

## 十一、下一步学习

**第3步：高级特性**
- 死信队列
- 消息追踪
- 消息重试策略
- 消费者负载均衡
- 集群部署

**第4步：生产实践**
- 性能优化
- 监控告警
- 故障排查
- 最佳实践

---

**学习时间**：60分钟  
**难度**：⭐⭐⭐（中等）  
**掌握标准**：能独立编写6个场景的代码，理解每行代码的作用

