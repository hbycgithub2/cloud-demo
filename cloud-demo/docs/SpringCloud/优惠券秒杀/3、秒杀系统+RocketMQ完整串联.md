# 秒杀系统+RocketMQ完整串联讲解

> **项目位置：** D:\code\cloud-demo\cloud-demo\seckill-demo  
> **技术栈：** Spring Boot + Redis + RocketMQ + MySQL + MyBatis  
> **核心目标：** 掌握秒杀系统完整流程 + RocketMQ在秒杀中的应用

---

## 一、项目整体架构

### 1.1 技术架构图
```
用户请求（10万并发）
    ↓
seckill-service（秒杀服务，8080端口）
    ├─ Redis Lua脚本扣库存（15ms）
    ├─ 标记用户已秒杀
    ├─ 生成订单号
    ├─ 发送RocketMQ消息 ← 【RocketMQ核心应用点】
    └─ 返回"秒杀成功"
         ↓
    RocketMQ（消息队列）
         ├─ Topic: seckill-topic
         ├─ 削峰填谷：10万请求 → 1000个/秒慢慢消费
         └─ 保证消息不丢失
         ↓
seckill-consumer（订单服务，8081端口）
    ├─ 消费RocketMQ消息 ← 【RocketMQ核心应用点】
    ├─ 扣减MySQL库存（乐观锁）
    ├─ 创建订单
    ├─ 记录库存日志
    └─ 记录用户秒杀记录
```

### 1.2 四张数据库表
```sql
cloud_seckill数据库：
├── tb_seckill_product     秒杀商品表（stock, version）
├── tb_seckill_order       秒杀订单表（order_no, user_id, product_id）
├── tb_stock_log           库存日志表（before_stock, after_stock）
└── tb_user_seckill        用户秒杀记录表（唯一索引防重复）
```

---

## 二、RocketMQ在秒杀系统中的3大核心作用

### 2.1 作用1：削峰填谷（最重要⭐）

**问题：**
```
秒杀开始瞬间：10万用户同时点击"立即抢购"
如果直接写MySQL：10万并发 → MySQL崩溃
```

**RocketMQ解决方案：**
```
第1步：Redis快速扣库存（15ms）→ 发MQ消息 → 返回"秒杀成功"
第2步：RocketMQ削峰：10万请求 → 存储到消息队列
第3步：Consumer慢慢消费：1000个/秒 → MySQL压力小
```

**效果对比：**
| 指标 | 不用MQ | 用MQ |
|------|--------|------|
| 用户等待时间 | 200ms | 15ms |
| MySQL并发压力 | 10万QPS | 1000QPS |
| 系统稳定性 | 容易崩溃 | 稳定 |

---

### 2.2 作用2：异步解耦

**问题：**
```java
// 同步处理：用户等200ms
public void seckill() {
    redis.decrStock();        // 15ms
    mysql.createOrder();      // 100ms
    mysql.updateStock();      // 50ms
    mysql.insertLog();        // 35ms
    // 用户等待：15+100+50+35 = 200ms
}
```

**RocketMQ解决方案：**
```java
// 异步处理：用户只等15ms
public void seckill() {
    redis.decrStock();        // 15ms
    mq.send(message);         // 5ms
    return "秒杀成功";
    // 用户等待：15+5 = 20ms
}

// Consumer异步处理（用户无感知）
public void consume() {
    mysql.createOrder();      // 100ms
    mysql.updateStock();      // 50ms
    mysql.insertLog();        // 35ms
}
```

---

### 2.3 作用3：保证消息不丢失

**RocketMQ保证机制：**
1. **持久化**：消息写入磁盘，Broker宕机也不丢
2. **重试机制**：消费失败自动重试（最多16次）
3. **死信队列**：重试16次还失败，进入死信队列人工处理

---

## 三、秒杀系统完整流程（9步）

### 第1步：启动环境
```bash
# 启动MySQL
mysql -uroot -proot

# 启动Redis
redis-server

# 启动RocketMQ NameServer
cd D:\rocketmq\bin
start mqnamesrv.cmd

# 启动RocketMQ Broker
start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
```

### 第2步：初始化数据
```sql
-- 创建数据库
CREATE DATABASE cloud_seckill;

-- 创建4张表
CREATE TABLE tb_seckill_product (...);
CREATE TABLE tb_seckill_order (...);
CREATE TABLE tb_stock_log (...);
CREATE TABLE tb_user_seckill (...);

-- 插入3个商品
INSERT INTO tb_seckill_product VALUES 
(1, 'iPhone 15 Pro 256GB', 10000, 6999.00, ...),
(2, 'MacBook Pro 14', 5000, 12999.00, ...),
(3, 'AirPods Pro', 20000, 1999.00, ...);
```

### 第3步：启动服务
```bash
# 启动seckill-service（8080端口）
java -jar seckill-service.jar

# 启动seckill-consumer（8081端口）
java -jar seckill-consumer.jar
```

### 第4步：库存预热（运营人员操作）
```bash
# 调用预热接口
POST /seckill/preload
{
  "productId": 1
}

# 执行流程：
# 1. 查询MySQL：SELECT stock FROM tb_seckill_product WHERE id=1 → 10000
# 2. 写入Redis：SET seckill:stock:1 10000
# 3. 返回"库存预热成功"
```


### 第5步：用户秒杀（15ms）
```bash
# 用户1002点击"立即抢购"
POST /seckill/kill
{
  "userId": 1002,
  "productId": 1,
  "quantity": 1
}

# seckill-service处理（6个步骤）：
# 步骤1：检查用户是否已秒杀
EXISTS seckill:user:1002:1 → 0（未秒杀）

# 步骤2：Lua脚本扣减Redis库存
GET seckill:stock:1 → 10000
判断：10000 >= 1？是
DECRBY seckill:stock:1 1 → 9999

# 步骤3：标记用户已秒杀
SET seckill:user:1002:1 "1"

# 步骤4：生成订单号
订单号 = "SK" + 时间戳 + 用户ID + 商品ID
例如：SK177048864772010021

# 步骤5：发送RocketMQ消息 ← 核心
Topic: seckill-topic
Message: {userId:1002, productId:1, quantity:1, orderNo:"SK177..."}

# 步骤6：返回"秒杀成功"
用户等待时间：15ms
```

### 第6步：RocketMQ削峰填谷
```
10万用户同时秒杀
    ↓
Redis快速扣库存（15ms）→ 发送10万条消息到MQ
    ↓
RocketMQ消息队列（削峰填谷）
    ↓
Consumer慢慢消费（1000个/秒）→ MySQL压力小
```


### 第7步：RocketMQ推送消息（2秒后）
```
RocketMQ Broker → seckill-consumer
推送消息：{userId:1002, productId:1, quantity:1, orderNo:"SK177..."}
```

### 第8步：seckill-consumer处理订单（200ms）
```java
@RocketMQMessageListener(
    topic = "seckill-topic",
    consumerGroup = "seckill-consumer"
)
public class SeckillConsumer implements RocketMQListener<SeckillMessage> {
    @Override
    public void onMessage(SeckillMessage msg) {
        // 步骤1：查询商品信息
        Product product = productMapper.selectById(msg.getProductId());
        // → stock=10000, version=0
        
        // 步骤2：扣减MySQL库存（乐观锁）
        int rows = productMapper.updateStock(
            msg.getProductId(), 
            msg.getQuantity(), 
            product.getVersion()
        );
        // SQL: UPDATE tb_seckill_product 
        //      SET stock=stock-1, version=version+1 
        //      WHERE id=1 AND version=0
        // → stock变成9999, version变成1
        
        if (rows == 0) {
            log.error("库存扣减失败，版本冲突");
            return;
        }
        
        // 步骤3：创建订单
        orderMapper.insert(msg.getOrderNo(), msg.getUserId(), ...);
        // SQL: INSERT INTO tb_seckill_order VALUES (...)
        
        // 步骤4：记录库存日志
        stockLogMapper.insert(msg.getOrderNo(), 10000, 9999, ...);
        // SQL: INSERT INTO tb_stock_log VALUES (...)
        
        // 步骤5：记录用户秒杀记录
        userSeckillMapper.insert(msg.getUserId(), msg.getProductId(), ...);
        // SQL: INSERT INTO tb_user_seckill VALUES (...)
    }
}
```

### 第9步：数据一致性验证
```bash
# Redis数据
GET seckill:stock:1 → 9999
EXISTS seckill:user:1002:1 → 1

# MySQL数据
SELECT stock, version FROM tb_seckill_product WHERE id=1;
→ stock=9999, version=1

SELECT * FROM tb_seckill_order WHERE order_no='SK177...';
→ 1条记录

SELECT * FROM tb_stock_log WHERE order_no='SK177...';
→ 1条记录（before_stock=10000, after_stock=9999）

SELECT * FROM tb_user_seckill WHERE user_id=1002 AND product_id=1;
→ 1条记录

# 结论：✅ Redis = MySQL，数据完全一致
```


---

## 四、RocketMQ核心配置

### 4.1 Producer配置（seckill-service）

```java
@Configuration
public class RocketMQConfig {
    
    @Bean
    public RocketMQTemplate rocketMQTemplate() {
        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(defaultMQProducer());
        return template;
    }
    
    @Bean
    public DefaultMQProducer defaultMQProducer() {
        DefaultMQProducer producer = new DefaultMQProducer("seckill-producer");
        producer.setNamesrvAddr("127.0.0.1:9876");
        producer.setSendMsgTimeout(3000);  // 发送超时3秒
        producer.setRetryTimesWhenSendFailed(2);  // 失败重试2次
        return producer;
    }
}
```

### 4.2 Consumer配置（seckill-consumer）

```java
@RocketMQMessageListener(
    topic = "seckill-topic",
    consumerGroup = "seckill-consumer",
    consumeMode = ConsumeMode.CONCURRENTLY,  // 并发消费
    messageModel = MessageModel.CLUSTERING,  // 集群模式
    consumeThreadMax = 20  // 最大消费线程数
)
public class SeckillConsumer implements RocketMQListener<SeckillMessage> {
    @Override
    public void onMessage(SeckillMessage message) {
        // 消费逻辑
    }
}
```

### 4.3 发送消息代码

```java
@Service
public class SeckillService {
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    public void sendSeckillMessage(SeckillMessage msg) {
        // 同步发送（等待Broker确认）
        SendResult result = rocketMQTemplate.syncSend(
            "seckill-topic", 
            msg, 
            3000  // 超时时间3秒
        );
        
        log.info("消息发送成功：msgId={}", result.getMsgId());
    }
}
```


---

## 五、RocketMQ消息重试机制

### 5.1 消费失败自动重试

```java
@RocketMQMessageListener(
    topic = "seckill-topic",
    consumerGroup = "seckill-consumer"
)
public class SeckillConsumer implements RocketMQListener<SeckillMessage> {
    @Override
    public void onMessage(SeckillMessage msg) {
        try {
            // 处理订单
            processOrder(msg);
        } catch (Exception e) {
            log.error("消费失败，等待重试", e);
            throw new RuntimeException("消费失败");  // 抛异常触发重试
        }
    }
}
```

### 5.2 重试策略

| 重试次数 | 重试间隔 | 说明 |
|---------|---------|------|
| 第1次 | 10秒后 | 快速重试 |
| 第2次 | 30秒后 | - |
| 第3次 | 1分钟后 | - |
| 第4次 | 2分钟后 | - |
| 第5次 | 3分钟后 | - |
| ... | ... | - |
| 第16次 | 2小时后 | 最后一次 |
| 超过16次 | 进入死信队列 | 人工处理 |

### 5.3 死信队列处理

```java
@RocketMQMessageListener(
    topic = "%DLQ%seckill-consumer",  // 死信队列Topic
    consumerGroup = "dlq-consumer"
)
public class DLQConsumer implements RocketMQListener<SeckillMessage> {
    @Override
    public void onMessage(SeckillMessage msg) {
        // 记录到数据库
        dlqLogMapper.insert(msg);
        
        // 发送告警通知
        alertService.sendAlert("秒杀消息消费失败16次", msg);
        
        // 人工介入处理
        log.error("消息进入死信队列：{}", msg);
    }
}
```

