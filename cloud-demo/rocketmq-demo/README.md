# RocketMQ Demo 测试项目

## 项目简介
RocketMQ 6大核心功能完整演示项目，包含生产者、消费者、数据库操作。

## 项目结构
```
rocketmq-demo/
├── rocketmq-common/          # 公共模块（实体类）
├── rocketmq-producer/        # 生产者（端口8090）
└── rocketmq-consumer/        # 消费者（端口8091）
```

## 技术栈
- Spring Boot 2.3.9
- RocketMQ 2.2.1
- MyBatis
- MySQL 5.7+

## 数据库
- 数据库名：cloud_rocketmq
- 表数量：5张（订单、短信、库存、库存日志、优惠券）
- 初始化脚本：init.sql

## 6大核心功能

| 功能 | 场景 | 生产者接口 | 消费者 |
|------|------|-----------|--------|
| 普通消息 | 订单后发短信 | POST /message/send | SmsConsumer |
| 延迟消息 | 30分钟自动取消订单 | POST /message/sendDelay | CancelConsumer |
| 事务消息 | 订单和库存一致性 | POST /message/sendTransaction | StockConsumer |
| 顺序消息 | 订单状态按序变更 | POST /message/sendOrderly | StatusConsumer |
| 批量消息 | 批量发10000张优惠券 | POST /message/sendBatch | CouponConsumer |
| 消息过滤 | 只处理VIP订单 | POST /message/sendVip | VipSmsConsumer |

## 快速开始

### 1. 启动RocketMQ服务器
```bash
# 启动NameServer
start mqnamesrv.cmd

# 启动Broker
start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
```

### 2. 初始化数据库
```bash
# 执行init.sql脚本
mysql -uroot -p < init.sql
```

### 3. 启动服务
```bash
# 1. 启动生产者（8090端口）
cd rocketmq-producer
mvn spring-boot:run

# 2. 启动消费者（8091端口）
cd rocketmq-consumer
mvn spring-boot:run
```

### 4. 测试接口

#### 场景1：普通消息 - 发送短信通知
```bash
POST http://localhost:8090/message/send
Content-Type: application/json

{
  "userId": 1001,
  "productName": "iPhone 15 Pro",
  "price": 7999.00,
  "quantity": 1
}
```

**预期结果：**
- 生产者：订单保存成功，消息发送成功
- 消费者：收到消息，短信发送成功，tb_sms_log表新增记录

---

#### 场景2：延迟消息 - 自动取消超时订单
```bash
POST http://localhost:8090/message/sendDelay
Content-Type: application/json

{
  "userId": 1002,
  "productName": "MacBook Pro",
  "price": 12999.00,
  "quantity": 1
}
```

**预期结果：**
- 生产者：订单保存成功（status=0待支付），延迟消息发送成功
- 消费者：30分钟后收到消息，订单状态更新为-1（已取消）

**快速测试（不等30分钟）：**
修改MessageService.java第60行，将延迟级别改为3（10秒）：
```java
SendResult result = rocketMQTemplate.syncSend("cancel-topic", message, 3000, 3);  // 10秒后执行
```

---

#### 场景3：事务消息 - 订单和库存一致性
```bash
POST http://localhost:8090/message/sendTransaction
Content-Type: application/json

{
  "userId": 1003,
  "productName": "iPhone 15 Pro",
  "price": 7999.00,
  "quantity": 2
}
```

**预期结果：**
- 生产者：执行本地事务（保存订单），事务提交，消息发送成功
- 消费者：收到消息，扣减库存成功，tb_stock_log表新增记录

**验证数据一致性：**
```sql
-- 查询订单
SELECT * FROM tb_order WHERE order_no = 'ORD20260207...';

-- 查询库存
SELECT * FROM tb_stock WHERE product_name = 'iPhone 15 Pro';

-- 查询库存扣减日志
SELECT * FROM tb_stock_log WHERE order_no = 'ORD20260207...';
```

---

#### 场景4：顺序消息 - 订单状态变更
```bash
# 先创建一个订单（获取订单号）
POST http://localhost:8090/message/send
{
  "userId": 1004,
  "productName": "AirPods Pro",
  "price": 1999.00,
  "quantity": 1
}

# 然后按顺序发送状态变更消息
POST http://localhost:8090/message/sendOrderly
{
  "orderNo": "ORD20260207123456789",
  "status": 1
}

POST http://localhost:8090/message/sendOrderly
{
  "orderNo": "ORD20260207123456789",
  "status": 2
}

POST http://localhost:8090/message/sendOrderly
{
  "orderNo": "ORD20260207123456789",
  "status": 3
}
```

**预期结果：**
- 消费者：按顺序消费消息，订单状态依次变更：0→1→2→3

**验证顺序：**
```sql
SELECT * FROM tb_order WHERE order_no = 'ORD20260207123456789';
-- status应该是3（已完成）
```

---

#### 场景5：批量消息 - 批量发送优惠券
```bash
POST http://localhost:8090/message/sendBatch
Content-Type: application/json

{
  "userIds": [1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010]
}
```

**预期结果：**
- 生产者：批量发送10条消息成功
- 消费者：收到10条消息，tb_coupon表新增10条记录

**验证批量发送：**
```sql
SELECT COUNT(*) FROM tb_coupon WHERE user_id IN (1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010);
-- 应该返回10
```

---

#### 场景6：消息过滤 - 只处理VIP订单
```bash
POST http://localhost:8090/message/sendVip
Content-Type: application/json

{
  "userId": 1006,
  "productName": "AirPods Pro",
  "price": 1999.00,
  "quantity": 1
}
```

**预期结果：**
- 生产者：VIP订单保存成功（is_vip=1），消息发送成功（带VIP标签）
- 消费者：VipSmsConsumer收到消息，发送VIP专属短信

**对比测试：**
```bash
# 发送普通订单
POST http://localhost:8090/message/send
{
  "userId": 1007,
  "productName": "AirPods Pro",
  "price": 1999.00,
  "quantity": 1
}
```

**验证过滤：**
```sql
-- VIP订单短信（VipSmsConsumer）
SELECT * FROM tb_sms_log WHERE order_no IN (
  SELECT order_no FROM tb_order WHERE is_vip = 1
);

-- 普通订单短信（SmsConsumer）
SELECT * FROM tb_sms_log WHERE order_no IN (
  SELECT order_no FROM tb_order WHERE is_vip = 0
);
```

---

## 常见问题

### 1. RocketMQ连接失败
**错误：** connect to <127.0.0.1:9876> failed

**解决：**
- 检查RocketMQ是否启动：`jps -l`（应该看到NamesrvStartup和BrokerStartup）
- 检查端口是否被占用：`netstat -ano | findstr 9876`

### 2. 消费者收不到消息
**原因：** Topic不存在

**解决：**
- 启动Broker时添加参数：`autoCreateTopicEnable=true`
- 或手动创建Topic：
```bash
mqadmin updateTopic -n 127.0.0.1:9876 -t order-topic -c DefaultCluster
```

### 3. 延迟消息不生效
**原因：** 延迟级别设置错误

**解决：**
- RocketMQ只支持18个固定延迟级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
- 30分钟对应级别16

### 4. 事务消息回查失败
**原因：** 订单查询失败

**解决：**
- 检查OrderMapper.selectByOrderNo()方法
- 检查数据库连接

### 5. 顺序消息乱序
**原因：** 消费模式设置错误

**解决：**
- 确保@RocketMQMessageListener注解中设置：`consumeMode = ConsumeMode.ORDERLY`

---

## 监控和管理

### RocketMQ控制台
```bash
# 下载RocketMQ Dashboard
git clone https://github.com/apache/rocketmq-dashboard.git

# 启动
cd rocketmq-dashboard
mvn spring-boot:run

# 访问
http://localhost:8080
```

### 查看消息
```bash
# 查看Topic列表
mqadmin topicList -n 127.0.0.1:9876

# 查看消费者组
mqadmin consumerProgress -n 127.0.0.1:9876
```

---

## 性能测试

### 压测工具
使用JMeter或Apache Bench进行压测

### 压测场景
```bash
# 普通消息压测（1000并发，持续1分钟）
ab -n 60000 -c 1000 -p order.json -T application/json http://localhost:8090/message/send
```

### 预期性能
- 生产者QPS：10000+
- 消费者QPS：10000+
- 消息延迟：<10ms

---

## 项目总结

### 技术亮点
1. ✅ 6大核心功能完整演示
2. ✅ 真实业务场景（订单、库存、短信、优惠券）
3. ✅ 数据库操作（MyBatis）
4. ✅ 事务一致性（事务消息）
5. ✅ 性能优化（批量消息、顺序消息）

### 面试要点
1. **普通消息**：异步解耦，订单和短信分离
2. **延迟消息**：定时任务，自动取消超时订单
3. **事务消息**：强一致性，订单和库存同时成功或失败
4. **顺序消息**：保证顺序，订单状态按序变更
5. **批量消息**：高吞吐，批量发放优惠券
6. **消息过滤**：精准消费，只处理VIP订单

### 扩展方向
1. 添加消息重试机制
2. 添加死信队列处理
3. 添加消息追踪
4. 添加限流降级
5. 集成Sentinel流控

---

## 联系方式
如有问题，请查看RocketMQ官方文档：https://rocketmq.apache.org/
