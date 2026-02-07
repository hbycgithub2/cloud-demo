# 秒杀系统Demo

> **学习目标**：掌握Redis + RocketMQ + MySQL三剑客配合，实现高并发秒杀系统

---

## 一、系统架构

### 1.1 技术栈

```
┌─────────────────────────────────────┐
│  Redis（扛高并发，10万QPS）          │  ← Lua脚本原子扣库存
├─────────────────────────────────────┤
│  RocketMQ（削峰填谷）⭐核心⭐        │  ← 10万请求→1000慢慢消费
├─────────────────────────────────────┤
│  MySQL（持久化存储）                 │  ← 异步写入，乐观锁
└─────────────────────────────────────┘
```

### 1.2 模块结构

```
seckill-demo/
├── seckill-common/        （公共实体类）
├── seckill-service/       （秒杀服务，8092端口）
│   ├── Controller         （秒杀接口）
│   ├── Service            （Redis扣库存 + 发MQ）
│   └── Lua脚本            （原子扣减）
└── seckill-consumer/      （消费者，8093端口）
    ├── Listener           （消费MQ消息）
    └── Mapper             （MySQL写入）
```

---

## 二、核心流程

### 2.1 秒杀流程图

```
用户点击秒杀
    ↓
检查是否已秒杀（Redis）
    ↓
Lua脚本扣减Redis库存（原子操作）
    ↓
扣减成功？
    ├─ 是 → 标记用户已秒杀 → 发送MQ消息 → 返回"秒杀成功"
    └─ 否 → 返回"库存不足"
    ↓
消费者收到MQ消息
    ↓
扣减MySQL库存（乐观锁）
    ↓
创建订单
    ↓
记录库存日志
    ↓
记录用户秒杀记录
```

### 2.2 为什么需要Redis？

| 场景 | 不用Redis | 用Redis |
|------|-----------|---------|
| **QPS** | 1000（MySQL瓶颈） | 100000（Redis扛住） |
| **响应时间** | 100ms | 5ms |
| **超卖风险** | 高（并发问题） | 低（Lua原子操作） |

### 2.3 为什么需要MQ？

| 场景 | 不用MQ | 用MQ |
|------|--------|------|
| **高峰期** | MySQL崩溃 | MQ削峰，慢慢消费 |
| **用户体验** | 等待100ms | 立即返回5ms |
| **系统解耦** | 订单服务依赖库存服务 | 通过MQ解耦 |

---

## 三、核心代码

### 3.1 Lua脚本（原子扣库存）

```lua
-- KEYS[1]: 库存key
-- ARGV[1]: 扣减数量

local stock = redis.call('GET', KEYS[1])

if not stock then
    return -1  -- 库存不存在
end

stock = tonumber(stock)

if stock < tonumber(ARGV[1]) then
    return 0  -- 库存不足
end

redis.call('DECRBY', KEYS[1], ARGV[1])
return stock - tonumber(ARGV[1])  -- 返回扣减后库存
```

### 3.2 秒杀服务（SeckillService.java）

```java
public String seckill(SeckillDTO dto) {
    // 1. 检查用户是否已秒杀
    String userKey = "seckill:user:" + userId + ":" + productId;
    if (redisTemplate.hasKey(userKey)) {
        return null;  // 已秒杀
    }

    // 2. Redis扣减库存（Lua脚本）
    String stockKey = "seckill:stock:" + productId;
    Long result = redisTemplate.execute(stockScript, 
        Collections.singletonList(stockKey), quantity);

    if (result < 0) {
        return null;  // 库存不足
    }

    // 3. 标记用户已秒杀
    redisTemplate.opsForValue().set(userKey, "1");

    // 4. 发送MQ消息
    rocketMQTemplate.convertAndSend("seckill-topic", dto);

    // 5. 返回订单号
    return generateOrderNo(userId, productId);
}
```

### 3.3 消费者（SeckillOrderConsumer.java）

```java
@Override
public void onMessage(SeckillDTO dto) {
    // 1. 扣减MySQL库存（乐观锁）
    int rows = productMapper.reduceStock(productId, quantity);
    if (rows == 0) {
        // TODO: 回滚Redis库存
        return;
    }

    // 2. 创建订单
    orderMapper.insert(order);

    // 3. 记录库存日志
    stockLogMapper.insert(...);

    // 4. 记录用户秒杀记录
    userSeckillMapper.insert(...);
}
```

---

## 四、快速开始

### 4.1 环境准备

1. **启动MySQL**
```bash
mysql -uroot -p
source D:/code/cloud-demo/cloud-demo/seckill-demo/init.sql
```

2. **启动Redis**
```bash
redis-server
```

3. **启动RocketMQ**
```bash
# NameServer
cd D:\rocketmq\bin
start mqnamesrv.cmd

# Broker
start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
```

### 4.2 启动服务

1. **启动秒杀服务（8092）**
```bash
cd seckill-service
mvn spring-boot:run
```

2. **启动消费者（8093）**
```bash
cd seckill-consumer
mvn spring-boot:run
```

### 4.3 测试秒杀

1. **预热库存**
```bash
curl -X POST http://localhost:8092/seckill/preload/1
```

2. **秒杀**
```bash
curl -X POST http://localhost:8092/seckill/kill \
-H "Content-Type: application/json" \
-d "{\"userId\":1001,\"productId\":1,\"quantity\":1}"
```

3. **查看结果**
```sql
-- 查看Redis库存
redis-cli
GET seckill:stock:1

-- 查看MySQL库存
SELECT stock FROM tb_seckill_product WHERE id = 1;

-- 查看订单
SELECT * FROM tb_seckill_order ORDER BY id DESC LIMIT 10;
```

---

## 五、性能对比

| 指标 | 不用Redis | 用Redis |
|------|-----------|---------|
| **QPS** | 1000 | 100000 |
| **响应时间** | 100ms | 5ms |
| **超卖** | 有风险 | 无风险 |
| **并发** | 1000 | 100000 |

---

## 六、核心要点

### 6.1 Redis的作用
- ✅ 扛高并发（10万QPS）
- ✅ Lua脚本原子操作（防超卖）
- ✅ 快速响应（5ms）

### 6.2 MQ的作用
- ✅ 削峰填谷（10万→1000）
- ✅ 异步处理（立即返回）
- ✅ 系统解耦（订单↔库存）

### 6.3 MySQL的作用
- ✅ 持久化存储（真实库存）
- ✅ 乐观锁（防超卖）
- ✅ 最终一致性（Redis→MySQL）

---

## 七、常见问题

### Q1：Redis库存和MySQL库存不一致怎么办？
A：定时任务同步，或者消费失败时回滚Redis库存。

### Q2：如何防止超卖？
A：Redis用Lua脚本原子操作，MySQL用乐观锁。

### Q3：如何防止重复秒杀？
A：Redis记录用户秒杀记录，MySQL用唯一索引。

### Q4：如何提高性能？
A：Redis集群、MQ集群、MySQL读写分离。

---

**学习时间**：2小时  
**难度**：⭐⭐⭐⭐（中高）  
**掌握标准**：能独立实现秒杀系统，理解Redis+MQ+MySQL配合原理
