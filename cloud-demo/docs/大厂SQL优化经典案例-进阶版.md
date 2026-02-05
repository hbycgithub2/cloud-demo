# 大厂 SQL 优化经典案例 - 进阶版

> **说明**：本文档整理了更多大厂级别的 SQL 优化经典案例，涵盖更复杂的场景和更高级的优化技巧。

## 目录

1. [金融支付：账户余额扣减并发问题](#案例-1金融支付账户余额扣减并发问题)
2. [直播平台：弹幕实时写入优化](#案例-2直播平台弹幕实时写入优化)
3. [电商平台：秒杀库存扣减](#案例-3电商平台秒杀库存扣减)
4. [社交平台：关注关系查询优化](#案例-4社交平台关注关系查询优化)
5. [物流平台：轨迹查询优化](#案例-5物流平台轨迹查询优化)
6. [广告平台：实时竞价查询优化](#案例-6广告平台实时竞价查询优化)
7. [游戏平台：排行榜查询优化](#案例-7游戏平台排行榜查询优化)

---

## 案例 1：金融支付 - 账户余额扣减并发问题

### 背景

**场景**：支付平台的账户余额扣减

**典型问题**：
- 高并发扣减导致余额不准确
- 出现超扣（余额为负）
- 数据库死锁频繁
- 响应时间长（3-5 秒）

**技术挑战**：
- 需要保证强一致性（金融级别）
- 高并发（每秒数万笔）
- 不能超扣（余额不能为负）
- 需要记录每笔交易

---

### 问题 SQL

```sql
-- 原始方案（有问题）
-- 步骤 1：查询余额
SELECT balance FROM accounts WHERE user_id = 123456;

-- 步骤 2：判断余额是否足够
if (balance >= 100) {
  -- 步骤 3：扣减余额
  UPDATE accounts 
  SET balance = balance - 100 
  WHERE user_id = 123456;
}

-- 问题：
-- 1. 查询和更新之间有时间差
-- 2. 多个请求同时查询到相同余额
-- 3. 导致超扣
```

---

### 问题分析

```
┌─────────────────────────────────────────────────────────────┐
│                  并发扣减问题示例                              │
└─────────────────────────────────────────────────────────────┘

初始状态：
  账户余额：100 元

时间线：
  T1: 用户 A 发起支付 80 元
  T2: 用户 B 发起支付 80 元（几乎同时）

执行过程：
  
  T1: 请求 A 查询余额
    SELECT balance FROM accounts WHERE user_id = 123456;
    结果：balance = 100
  
  T2: 请求 B 查询余额（同时）
    SELECT balance FROM accounts WHERE user_id = 123456;
    结果：balance = 100
  
  T3: 请求 A 判断余额
    100 >= 80 ✅ 可以扣减
  
  T4: 请求 B 判断余额
    100 >= 80 ✅ 可以扣减
  
  T5: 请求 A 扣减余额
    UPDATE accounts SET balance = 100 - 80 = 20 WHERE user_id = 123456;
  
  T6: 请求 B 扣减余额
    UPDATE accounts SET balance = 100 - 80 = 20 WHERE user_id = 123456;
    （覆盖了请求 A 的结果）

最终结果：
  账户余额：20 元
  
问题：
  ❌ 应该扣减 160 元（80 + 80）
  ❌ 实际只扣减了 80 元
  ❌ 账户多了 80 元（资损）
```

---

### 解决方案

#### 方案 1：悲观锁（FOR UPDATE）

```sql
-- 使用悲观锁
START TRANSACTION;

-- 查询并锁定记录
SELECT balance FROM accounts 
WHERE user_id = 123456 
FOR UPDATE;  -- 锁定这一行，其他事务必须等待

-- 判断余额
if (balance >= 100) {
  -- 扣减余额
  UPDATE accounts 
  SET balance = balance - 100 
  WHERE user_id = 123456;
  
  COMMIT;
} else {
  ROLLBACK;
}
```

**优点**：
- ✅ 保证数据一致性
- ✅ 不会超扣

**缺点**：
- ❌ 性能较低（锁等待）
- ❌ 可能死锁

---

#### 方案 2：乐观锁（版本号）

```sql
-- 添加版本号字段
ALTER TABLE accounts ADD COLUMN version INT DEFAULT 0;

-- 查询余额和版本号
SELECT balance, version FROM accounts WHERE user_id = 123456;
-- 结果：balance = 100, version = 5

-- 判断余额
if (balance >= 100) {
  -- 扣减余额（带版本号）
  UPDATE accounts 
  SET balance = balance - 100, 
      version = version + 1
  WHERE user_id = 123456 
    AND version = 5;  -- 版本号必须匹配
  
  -- 检查更新结果
  if (affected_rows == 0) {
    // 版本号不匹配，说明被其他事务修改了
    // 重试
  }
}
```

**优点**：
- ✅ 性能高（无锁等待）
- ✅ 不会超扣

**缺点**：
- ⚠️ 需要重试（更新失败时）

---

#### 方案 3：Redis 分布式锁 + 异步入库（推荐）

```java
// 使用 Redis 分布式锁
public boolean deductBalance(Long userId, BigDecimal amount) {
    String lockKey = "account:lock:" + userId;
    
    // 1. 获取分布式锁
    boolean locked = redis.setnx(lockKey, "1", 10);  // 10 秒过期
    if (!locked) {
        return false;  // 获取锁失败，稍后重试
    }
    
    try {
        // 2. 从 Redis 查询余额
        BigDecimal balance = redis.get("account:balance:" + userId);
        
        // 3. 判断余额
        if (balance.compareTo(amount) < 0) {
            return false;  // 余额不足
        }
        
        // 4. 扣减 Redis 余额
        redis.decrBy("account:balance:" + userId, amount);
        
        // 5. 发送消息到 Kafka（异步入库）
        kafka.send("account-transaction", {
            userId: userId,
            amount: amount,
            type: "deduct",
            timestamp: System.currentTimeMillis()
        });
        
        return true;
    } finally {
        // 6. 释放锁
        redis.del(lockKey);
    }
}

// Kafka 消费者（异步入库）
@KafkaListener(topics = "account-transaction")
public void handleTransaction(TransactionMessage msg) {
    // 批量写入 MySQL
    accountMapper.insertTransaction(msg);
    accountMapper.updateBalance(msg.getUserId(), msg.getAmount());
}
```

**优点**：
- ✅ 性能高（Redis 内存操作）
- ✅ 保证一致性（分布式锁）
- ✅ 异步入库（不阻塞用户）

**缺点**：
- ⚠️ 架构复杂（需要 Redis + Kafka）

---

### 完整流程图

```
┌─────────────────────────────────────────────────────────────┐
│          金融支付账户余额扣减 - 完整优化流程                    │
└─────────────────────────────────────────────────────────────┘

用户发起支付
  ↓
  支付金额：100 元

获取分布式锁
  ↓
  Redis SETNX account:lock:123456
  
  if (获取锁失败) {
    返回：系统繁忙，请稍后重试
  }

查询 Redis 余额
  ↓
  Redis GET account:balance:123456
  结果：balance = 500 元

判断余额
  ↓
  500 >= 100 ✅ 余额充足

扣减 Redis 余额
  ↓
  Redis DECRBY account:balance:123456 100
  新余额：400 元

发送消息到 Kafka
  ↓
  消息内容：
    userId: 123456
    amount: 100
    type: deduct
    timestamp: 1234567890

释放锁
  ↓
  Redis DEL account:lock:123456

返回成功
  ↓
  用户：支付成功

异步入库（Kafka 消费者）
  ↓
  1. 插入交易记录
     INSERT INTO transactions (user_id, amount, type, create_time)
     VALUES (123456, 100, 'deduct', NOW());
  
  2. 更新账户余额
     UPDATE accounts 
     SET balance = balance - 100 
     WHERE user_id = 123456;

────────────────────────────────────────────────────────────

性能对比：
  
  方案 1（悲观锁）：
    响应时间：100-200ms
    TPS：5000
  
  方案 2（乐观锁）：
    响应时间：50-100ms
    TPS：10000
  
  方案 3（Redis + Kafka）：
    响应时间：10-20ms
    TPS：50000+
```

---

## 案例 2：直播平台 - 弹幕实时写入优化

### 背景

**场景**：直播平台的弹幕发送

**典型问题**：
- 弹幕写入延迟（1-2 秒）
- 数据库写入 TPS 瓶颈
- 热门直播间弹幕量巨大（每秒数万条）
- 用户看不到自己发的弹幕

**技术挑战**：
- 超高并发写入（每秒数万到数十万条）
- 需要实时展示
- 历史弹幕数据量巨大（千亿级）

---

### 问题 SQL

```sql
-- 原始方案（有问题）
INSERT INTO danmu (room_id, user_id, content, create_time)
VALUES (12345, 67890, '主播666', NOW());

-- 问题：
-- 1. 每条弹幕都写入 MySQL
-- 2. 热门直播间每秒数万条弹幕
-- 3. MySQL 写入 TPS 瓶颈（单机 1-2 万）
-- 4. 写入延迟导致用户看不到自己的弹幕
```

---

### 解决方案

#### 方案 1：Redis + 异步批量入库

```java
// 发送弹幕
public void sendDanmu(Long roomId, Long userId, String content) {
    // 1. 写入 Redis（实时）
    String key = "danmu:room:" + roomId;
    DanmuMessage msg = new DanmuMessage(userId, content, System.currentTimeMillis());
    
    // 使用 Redis List 存储最近的弹幕
    redis.lpush(key, JSON.toJSONString(msg));
    redis.ltrim(key, 0, 999);  // 只保留最近 1000 条
    
    // 2. 发送到 Kafka（异步入库）
    kafka.send("danmu-topic", msg);
    
    // 3. 通过 WebSocket 推送给所有观众
    webSocket.broadcast(roomId, msg);
}

// Kafka 消费者（批量入库）
@KafkaListener(topics = "danmu-topic")
public void handleDanmu(List<DanmuMessage> messages) {
    // 批量插入 MySQL（每次 1000 条）
    danmuMapper.batchInsert(messages);
}
```

---

#### 方案 2：分库分表 + 冷热分离

```sql
-- 按直播间 ID 分表
danmu_0, danmu_1, danmu_2, ..., danmu_99

-- 分表规则
table_index = room_id % 100

-- 冷热分离
-- 热数据：最近 7 天的弹幕（MySQL）
-- 冷数据：7 天前的弹幕（HBase）

-- 定时任务：每天凌晨归档
INSERT INTO hbase_danmu 
SELECT * FROM danmu_0 
WHERE create_time < DATE_SUB(NOW(), INTERVAL 7 DAY);

DELETE FROM danmu_0 
WHERE create_time < DATE_SUB(NOW(), INTERVAL 7 DAY);
```

---

### 完整流程图

```
┌─────────────────────────────────────────────────────────────┐
│            直播平台弹幕实时写入 - 完整优化流程                  │
└─────────────────────────────────────────────────────────────┘

用户发送弹幕
  ↓
  内容："主播666"

写入 Redis（实时）
  ↓
  Redis LPUSH danmu:room:12345 {"userId":67890,"content":"主播666"}
  Redis LTRIM danmu:room:12345 0 999  // 只保留最近 1000 条
  
  耗时：1-2ms

推送给所有观众（WebSocket）
  ↓
  WebSocket.broadcast(roomId, danmuMessage)
  
  所有观众立即看到弹幕
  耗时：5-10ms

发送到 Kafka（异步）
  ↓
  Kafka.send("danmu-topic", danmuMessage)
  
  不阻塞用户

Kafka 消费者（批量入库）
  ↓
  每 1 秒或累积 1000 条弹幕
  批量插入 MySQL
  
  INSERT INTO danmu_0 (room_id, user_id, content, create_time)
  VALUES 
    (12345, 67890, '主播666', NOW()),
    (12345, 67891, '666', NOW()),
    ...
    (12345, 68890, '关注主播', NOW());

定时归档（每天凌晨）
  ↓
  将 7 天前的弹幕归档到 HBase
  从 MySQL 删除

────────────────────────────────────────────────────────────

性能对比：
  
  原始方案：
    写入延迟：100-200ms
    TPS：10000
    用户体验：❌ 看不到自己的弹幕
  
  优化方案：
    写入延迟：1-2ms（Redis）
    TPS：100000+
    用户体验：✅ 立即看到弹幕
```

---

## 案例 3：电商平台 - 秒杀库存扣减

### 背景

**场景**：电商秒杀活动的库存扣减

**典型问题**：
- 超卖（库存为负）
- 数据库死锁
- 响应时间长（用户抢不到）
- 库存扣减不准确

**技术挑战**：
- 超高并发（每秒数十万请求）
- 库存有限（可能只有 100 件）
- 需要保证不超卖
- 需要快速响应

---

### 问题 SQL

```sql
-- 原始方案（有问题）
-- 步骤 1：查询库存
SELECT stock FROM products WHERE id = 12345;

-- 步骤 2：判断库存
if (stock > 0) {
  -- 步骤 3：扣减库存
  UPDATE products 
  SET stock = stock - 1 
  WHERE id = 12345;
}

-- 问题：
-- 1. 查询和更新之间有时间差
-- 2. 多个请求同时查询到相同库存
-- 3. 导致超卖
```

---

### 解决方案

#### 方案 1：数据库层面防止超卖

```sql
-- 方案 1.1：使用 WHERE 条件
UPDATE products 
SET stock = stock - 1 
WHERE id = 12345 
  AND stock > 0;  -- 关键：只有库存大于 0 才能扣减

-- 检查更新结果
if (affected_rows == 0) {
  // 库存不足或商品不存在
  return "抢购失败";
}

-- 优点：
-- ✅ 简单
-- ✅ 不会超卖

-- 缺点：
-- ❌ 高并发下性能差（锁竞争）
```

---

#### 方案 2：Redis 预扣库存 + 异步入库（推荐）

```java
// 秒杀接口
public boolean seckill(Long productId, Long userId) {
    String stockKey = "seckill:stock:" + productId;
    
    // 1. Redis 原子扣减库存
    Long stock = redis.decr(stockKey);
    
    if (stock < 0) {
        // 库存不足，恢复库存
        redis.incr(stockKey);
        return false;
    }
    
    // 2. 发送消息到 Kafka（异步创建订单）
    kafka.send("seckill-order", {
        productId: productId,
        userId: userId,
        timestamp: System.currentTimeMillis()
    });
    
    return true;
}

// Kafka 消费者（异步创建订单）
@KafkaListener(topics = "seckill-order")
public void createOrder(SeckillMessage msg) {
    // 1. 创建订单
    orderMapper.insert(msg);
    
    // 2. 扣减 MySQL 库存
    productMapper.decrStock(msg.getProductId());
}
```

---

#### 方案 3：Lua 脚本保证原子性

```lua
-- Redis Lua 脚本
local stock_key = KEYS[1]
local order_key = KEYS[2]
local user_id = ARGV[1]

-- 检查库存
local stock = redis.call('get', stock_key)
if not stock or tonumber(stock) <= 0 then
    return 0  -- 库存不足
end

-- 检查用户是否已经抢购
local exists = redis.call('sismember', order_key, user_id)
if exists == 1 then
    return -1  -- 用户已经抢购过
end

-- 扣减库存
redis.call('decr', stock_key)

-- 记录用户抢购
redis.call('sadd', order_key, user_id)

return 1  -- 抢购成功
```

```java
// Java 调用 Lua 脚本
public boolean seckill(Long productId, Long userId) {
    String stockKey = "seckill:stock:" + productId;
    String orderKey = "seckill:users:" + productId;
    
    // 执行 Lua 脚本（原子操作）
    Long result = redis.eval(luaScript, 
        Arrays.asList(stockKey, orderKey), 
        Arrays.asList(userId.toString())
    );
    
    if (result == 1) {
        // 抢购成功，异步创建订单
        kafka.send("seckill-order", new SeckillMessage(productId, userId));
        return true;
    }
    
    return false;
}
```

---

### 完整流程图

```
┌─────────────────────────────────────────────────────────────┐
│            电商秒杀库存扣减 - 完整优化流程                      │
└─────────────────────────────────────────────────────────────┘

秒杀开始前
  ↓
  初始化 Redis 库存
  Redis SET seckill:stock:12345 100

用户点击秒杀
  ↓
  发送请求：抢购商品 12345

执行 Lua 脚本（原子操作）
  ↓
  1. 检查库存
     stock = Redis GET seckill:stock:12345
     if (stock <= 0) {
       return "库存不足"
     }
  
  2. 检查用户是否已抢购
     exists = Redis SISMEMBER seckill:users:12345 userId
     if (exists) {
       return "您已经抢购过了"
     }
  
  3. 扣减库存
     Redis DECR seckill:stock:12345
  
  4. 记录用户
     Redis SADD seckill:users:12345 userId
  
  耗时：1-2ms

返回结果
  ↓
  用户：抢购成功！

发送消息到 Kafka（异步）
  ↓
  消息内容：
    productId: 12345
    userId: 67890
    timestamp: 1234567890

Kafka 消费者（异步创建订单）
  ↓
  1. 创建订单
     INSERT INTO orders (product_id, user_id, status, create_time)
     VALUES (12345, 67890, 'pending', NOW());
  
  2. 扣减 MySQL 库存
     UPDATE products 
     SET stock = stock - 1 
     WHERE id = 12345;

────────────────────────────────────────────────────────────

性能对比：
  
  原始方案（MySQL）：
    TPS：5000
    响应时间：50-100ms
    超卖风险：❌ 高
  
  优化方案（Redis + Lua）：
    TPS：100000+
    响应时间：1-2ms
    超卖风险：✅ 无
```

---

## 案例 4：社交平台 - 关注关系查询优化

### 背景

**场景**：社交平台的关注/粉丝查询

**典型问题**：
- 查询"共同关注"慢（5-10 秒）
- 查询"可能认识的人"慢
- 关注关系数据量巨大（百亿级）
- 复杂的关系查询

**技术挑战**：
- 需要多表关联
- 需要计算交集、并集
- 数据量巨大

---

### 问题 SQL

```sql
-- 查询共同关注（慢）
SELECT f1.following_id 
FROM follows f1
INNER JOIN follows f2 
  ON f1.following_id = f2.following_id
WHERE f1.follower_id = 123456  -- 我关注的人
  AND f2.follower_id = 789012  -- 对方关注的人
LIMIT 20;

-- 问题：
-- 1. 需要关联两次 follows 表
-- 2. follows 表有百亿条数据
-- 3. 执行时间：5-10 秒
```

---

### 解决方案

#### 方案 1：使用 Redis Set 存储关注关系

```java
// 关注用户
public void follow(Long followerId, Long followingId) {
    // 1. 写入 MySQL
    followMapper.insert(followerId, followingId);
    
    // 2. 写入 Redis
    redis.sadd("following:" + followerId, followingId);  // 我关注的人
    redis.sadd("followers:" + followingId, followerId);  // 关注我的人
}

// 查询共同关注
public List<Long> getCommonFollowing(Long userId1, Long userId2) {
    // Redis Set 交集（毫秒级）
    Set<Long> common = redis.sinter(
        "following:" + userId1,
        "following:" + userId2
    );
    return new ArrayList<>(common);
}

// 查询可能认识的人（二度好友）
public List<Long> getSuggestedUsers(Long userId) {
    // 1. 获取我关注的人
    Set<Long> myFollowing = redis.smembers("following:" + userId);
    
    // 2. 获取他们关注的人（二度好友）
    Set<Long> suggested = new HashSet<>();
    for (Long friendId : myFollowing) {
        Set<Long> friendFollowing = redis.smembers("following:" + friendId);
        suggested.addAll(friendFollowing);
    }
    
    // 3. 排除我已经关注的人
    suggested.removeAll(myFollowing);
    suggested.remove(userId);  // 排除自己
    
    return new ArrayList<>(suggested);
}
```

---

#### 方案 2：使用图数据库（Neo4j）

```cypher
-- 创建用户节点
CREATE (u:User {id: 123456, name: '张三'})

-- 创建关注关系
MATCH (u1:User {id: 123456}), (u2:User {id: 789012})
CREATE (u1)-[:FOLLOWS]->(u2)

-- 查询共同关注（毫秒级）
MATCH (me:User {id: 123456})-[:FOLLOWS]->(common)<-[:FOLLOWS]-(other:User {id: 789012})
RETURN common
LIMIT 20;

-- 查询二度好友（可能认识的人）
MATCH (me:User {id: 123456})-[:FOLLOWS]->()-[:FOLLOWS]->(suggested)
WHERE NOT (me)-[:FOLLOWS]->(suggested) AND me <> suggested
RETURN suggested
LIMIT 20;
```

**优点**：
- ✅ 专为关系查询设计
- ✅ 查询速度快（毫秒级）
- ✅ 支持复杂的关系查询

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              关注关系查询 - 性能对比                            │
└─────────────────────────────────────────────────────────────┘

场景：查询共同关注（用户 A 和用户 B）

方案 1：MySQL（原始）
  ↓
  SQL：
    SELECT f1.following_id 
    FROM follows f1
    INNER JOIN follows f2 
      ON f1.following_id = f2.following_id
    WHERE f1.follower_id = 123456 
      AND f2.follower_id = 789012;
  
  执行时间：5-10 秒
  扫描行数：1 亿行
  
  问题：
    ❌ 太慢
    ❌ 数据库压力大

方案 2：Redis Set
  ↓
  命令：
    SINTER following:123456 following:789012
  
  执行时间：10-50ms
  
  优点：
    ✅ 快
    ✅ 简单

方案 3：Neo4j（图数据库）
  ↓
  Cypher：
    MATCH (me:User {id: 123456})-[:FOLLOWS]->(common)<-[:FOLLOWS]-(other:User {id: 789012})
    RETURN common;
  
  执行时间：5-20ms
  
  优点：
    ✅ 最快
    ✅ 支持复杂关系查询
```

---

## 案例 5：物流平台 - 轨迹查询优化

### 背景

**场景**：物流平台的包裹轨迹查询

**典型问题**：
- 轨迹查询慢（2-3 秒）
- 轨迹数据量巨大（千亿级）
- 需要按时间排序
- 历史轨迹和实时轨迹混在一起

**技术挑战**：
- 每个包裹有 10-50 条轨迹
- 需要快速查询最新轨迹
- 历史数据需要归档

---

### 问题 SQL

```sql
-- 查询包裹轨迹（慢）
SELECT * FROM package_tracks 
WHERE package_id = '1234567890'
ORDER BY create_time DESC;

-- 问题：
-- 1. package_tracks 表有千亿条数据
-- 2. 即使有索引，查询也慢
-- 3. 历史轨迹和实时轨迹混在一起
```

---

### 解决方案

#### 方案 1：冷热分离 + 分表

```sql
-- 热数据表（最近 30 天）
CREATE TABLE package_tracks_hot (
  id BIGINT PRIMARY KEY,
  package_id VARCHAR(50),
  status VARCHAR(20),
  location VARCHAR(100),
  create_time DATETIME,
  INDEX idx_package_time (package_id, create_time)
) ENGINE=InnoDB;

-- 冷数据表（30 天前，按月分表）
CREATE TABLE package_tracks_2024_01 (
  id BIGINT PRIMARY KEY,
  package_id VARCHAR(50),
  status VARCHAR(20),
  location VARCHAR(100),
  create_time DATETIME,
  INDEX idx_package_time (package_id, create_time)
) ENGINE=InnoDB;

-- 查询逻辑
public List<Track> getPackageTracks(String packageId) {
    // 1. 先查热数据
    List<Track> tracks = trackMapper.selectFromHot(packageId);
    
    // 2. 如果热数据不够，再查冷数据
    if (tracks.size() < 10) {
        List<Track> coldTracks = trackMapper.selectFromCold(packageId);
        tracks.addAll(coldTracks);
    }
    
    return tracks;
}
```

---

#### 方案 2：使用 HBase 存储轨迹

```java
// HBase 表设计
// RowKey: packageId + timestamp（倒序）
// 例如：1234567890_9999999999999 - timestamp

// 写入轨迹
public void addTrack(String packageId, Track track) {
    long reverseTimestamp = Long.MAX_VALUE - System.currentTimeMillis();
    String rowKey = packageId + "_" + reverseTimestamp;
    
    hbaseTemplate.put("package_tracks", rowKey, "info", "status", track.getStatus());
    hbaseTemplate.put("package_tracks", rowKey, "info", "location", track.getLocation());
}

// 查询轨迹（自动按时间倒序）
public List<Track> getPackageTracks(String packageId) {
    String startRow = packageId + "_0";
    String stopRow = packageId + "_9";
    
    List<Track> tracks = hbaseTemplate.scan("package_tracks", startRow, stopRow, 20);
    return tracks;
}
```

**优点**：
- ✅ 查询速度快（毫秒级）
- ✅ 支持海量数据
- ✅ 自动按时间排序（RowKey 设计）

---

## 案例 6：广告平台 - 实时竞价查询优化

### 背景

**场景**：广告平台的实时竞价（RTB）

**典型问题**：
- 竞价查询慢（100-200ms）
- 需要查询大量广告主的出价
- 需要复杂的过滤条件（地域、年龄、兴趣）
- 响应时间要求极高（< 50ms）

**技术挑战**：
- 超低延迟要求（< 50ms）
- 需要查询数百个广告主
- 需要复杂的匹配逻辑

---

### 解决方案

#### 方案 1：使用 Redis + Bitmap

```java
// 广告定向数据存储
// 使用 Bitmap 存储用户标签

// 设置用户标签
public void setUserTag(Long userId, String tag) {
    // tag = "male", "age_20_30", "interest_sports"
    redis.setbit("tag:" + tag, userId, 1);
}

// 查询符合条件的广告
public List<Ad> getMatchedAds(Long userId) {
    // 1. 获取用户标签
    Set<String> userTags = getUserTags(userId);
    
    // 2. 查询每个标签对应的广告（预先计算好）
    Set<Long> adIds = new HashSet<>();
    for (String tag : userTags) {
        Set<Long> tagAds = redis.smembers("ads:tag:" + tag);
        adIds.addAll(tagAds);
    }
    
    // 3. 批量查询广告详情（从 Redis）
    List<Ad> ads = redis.mget(adIds.stream()
        .map(id -> "ad:" + id)
        .collect(Collectors.toList()));
    
    // 4. 排序（按出价）
    ads.sort((a, b) -> b.getBid().compareTo(a.getBid()));
    
    return ads.subList(0, Math.min(10, ads.size()));
}
```

---

#### 方案 2：使用 Elasticsearch

```java
// 广告索引结构
{
  "ad_id": 12345,
  "title": "iPhone 15 Pro",
  "bid": 5.0,
  "target": {
    "gender": ["male"],
    "age": [20, 30],
    "interests": ["tech", "phone"],
    "locations": ["beijing", "shanghai"]
  }
}

// 查询匹配的广告
public List<Ad> getMatchedAds(User user) {
    SearchRequest request = new SearchRequest("ads");
    SearchSourceBuilder builder = new SearchSourceBuilder();
    
    // 构建查询条件
    BoolQueryBuilder query = QueryBuilders.boolQuery()
        .must(QueryBuilders.termQuery("target.gender", user.getGender()))
        .must(QueryBuilders.rangeQuery("target.age")
            .gte(user.getAge()).lte(user.getAge()))
        .should(QueryBuilders.termsQuery("target.interests", user.getInterests()))
        .should(QueryBuilders.termsQuery("target.locations", user.getLocation()));
    
    builder.query(query);
    builder.sort("bid", SortOrder.DESC);  // 按出价排序
    builder.size(10);
    
    request.source(builder);
    SearchResponse response = client.search(request);
    
    return parseAds(response);
}
```

---

## 案例 7：游戏平台 - 排行榜查询优化

### 背景

**场景**：游戏平台的实时排行榜

**典型问题**：
- 排行榜查询慢（1-2 秒）
- 需要实时更新
- 需要支持分页
- 玩家数量巨大（千万级）

**技术挑战**：
- 需要实时排序
- 需要快速查询排名
- 需要支持分数更新

---

### 问题 SQL

```sql
-- 查询排行榜（慢）
SELECT user_id, score, 
       ROW_NUMBER() OVER (ORDER BY score DESC) as rank
FROM game_scores
ORDER BY score DESC
LIMIT 100;

-- 查询用户排名（慢）
SELECT COUNT(*) + 1 as rank
FROM game_scores
WHERE score > (SELECT score FROM game_scores WHERE user_id = 123456);

-- 问题：
-- 1. 每次查询都要全表排序
-- 2. 查询用户排名需要扫描全表
-- 3. 执行时间：1-2 秒
```

---

### 解决方案：使用 Redis Sorted Set

```java
// 更新分数
public void updateScore(Long userId, int score) {
    // Redis Sorted Set（自动排序）
    redis.zadd("game:leaderboard", score, userId.toString());
}

// 查询排行榜（前 100 名）
public List<RankInfo> getLeaderboard(int page, int size) {
    int start = (page - 1) * size;
    int end = start + size - 1;
    
    // 查询指定范围（倒序）
    Set<ZSetOperations.TypedTuple<String>> result = 
        redis.zrevrangeWithScores("game:leaderboard", start, end);
    
    List<RankInfo> leaderboard = new ArrayList<>();
    int rank = start + 1;
    for (ZSetOperations.TypedTuple<String> tuple : result) {
        leaderboard.add(new RankInfo(
            Long.parseLong(tuple.getValue()),
            tuple.getScore().intValue(),
            rank++
        ));
    }
    
    return leaderboard;
}

// 查询用户排名
public RankInfo getUserRank(Long userId) {
    // 查询排名（O(log N)）
    Long rank = redis.zrevrank("game:leaderboard", userId.toString());
    
    // 查询分数
    Double score = redis.zscore("game:leaderboard", userId.toString());
    
    return new RankInfo(userId, score.intValue(), rank + 1);
}

// 查询用户周围的排名
public List<RankInfo> getNearbyRanks(Long userId, int range) {
    // 1. 查询用户排名
    Long rank = redis.zrevrank("game:leaderboard", userId.toString());
    
    // 2. 查询前后 range 名
    int start = Math.max(0, rank.intValue() - range);
    int end = rank.intValue() + range;
    
    Set<ZSetOperations.TypedTuple<String>> result = 
        redis.zrevrangeWithScores("game:leaderboard", start, end);
    
    // 3. 构建结果
    List<RankInfo> nearby = new ArrayList<>();
    int currentRank = start + 1;
    for (ZSetOperations.TypedTuple<String> tuple : result) {
        nearby.add(new RankInfo(
            Long.parseLong(tuple.getValue()),
            tuple.getScore().intValue(),
            currentRank++
        ));
    }
    
    return nearby;
}
```

---

### 性能对比

```
┌─────────────────────────────────────────────────────────────┐
│              排行榜查询 - 性能对比                              │
└─────────────────────────────────────────────────────────────┘

场景：查询排行榜前 100 名

方案 1：MySQL（原始）
  ↓
  SQL：
    SELECT user_id, score, 
           ROW_NUMBER() OVER (ORDER BY score DESC) as rank
    FROM game_scores
    ORDER BY score DESC
    LIMIT 100;
  
  执行时间：1-2 秒
  扫描行数：1000 万行
  
  问题：
    ❌ 每次都要全表排序
    ❌ 太慢

方案 2：Redis Sorted Set
  ↓
  命令：
    ZREVRANGE game:leaderboard 0 99 WITHSCORES
  
  执行时间：1-5ms
  
  优点：
    ✅ 自动排序
    ✅ 查询速度快
    ✅ 支持分页
    ✅ 支持查询排名

────────────────────────────────────────────────────────────

场景：查询用户排名

方案 1：MySQL（原始）
  ↓
  SQL：
    SELECT COUNT(*) + 1 as rank
    FROM game_scores
    WHERE score > (SELECT score FROM game_scores WHERE user_id = 123456);
  
  执行时间：500ms - 1 秒
  扫描行数：1000 万行

方案 2：Redis Sorted Set
  ↓
  命令：
    ZREVRANK game:leaderboard 123456
  
  执行时间：1ms
  
  性能提升：500-1000 倍
```

---

## 总结：大厂 SQL 优化核心思路

### 1. 分层存储

```
热数据（高频访问）：
  ✅ Redis（内存）
  ✅ 响应时间：1-10ms

温数据（中频访问）：
  ✅ MySQL（SSD）
  ✅ 响应时间：10-100ms

冷数据（低频访问）：
  ✅ HBase / 对象存储
  ✅ 响应时间：100-500ms
```

---

### 2. 读写分离

```
写操作：
  ✅ 主库（Master）
  ✅ 保证一致性

读操作：
  ✅ 从库（Slave）
  ✅ 分担压力
  ✅ 可以有多个从库
```

---

### 3. 异步处理

```
实时响应：
  ✅ 写入 Redis / Kafka
  ✅ 立即返回

异步入库：
  ✅ 消费 Kafka 消息
  ✅ 批量写入 MySQL
  ✅ 不阻塞用户
```

---

### 4. 选择合适的存储

```
关系数据：
  ✅ MySQL

缓存数据：
  ✅ Redis

搜索数据：
  ✅ Elasticsearch

时序数据：
  ✅ InfluxDB / HBase

图关系数据：
  ✅ Neo4j

文件数据：
  ✅ 对象存储（OSS / S3）
```

---

### 5. 优化技巧总结

```
数据库层面：
  ✅ 添加索引
  ✅ 优化 SQL
  ✅ 分库分表
  ✅ 读写分离

缓存层面：
  ✅ Redis 缓存热点数据
  ✅ 本地缓存（Guava Cache）
  ✅ CDN 缓存静态资源

架构层面：
  ✅ 异步处理（Kafka）
  ✅ 分布式锁（Redis）
  ✅ 限流降级（Sentinel）
  ✅ 熔断（Hystrix）

数据层面：
  ✅ 冷热分离
  ✅ 历史数据归档
  ✅ 数据压缩
```

---

## 一句话总结

**大厂 SQL 优化的核心：不是单纯优化 SQL，而是选择合适的存储方案 + 分层架构 + 异步处理 + 缓存优化。90% 的性能问题都可以通过 Redis 缓存 + 异步处理解决！**

