# SQL 优化问题和解决方案 - 人话版

## 目录

1. [慢查询问题](#问题-1慢查询)
2. [索引失效问题](#问题-2索引失效)
3. [深分页问题](#问题-3深分页)
4. [N+1 查询问题](#问题-4n1-查询)
5. [大表查询问题](#问题-5大表查询)
6. [SQL 优化流程](#sql-优化完整流程)

---

## 问题 1：慢查询

### 什么是慢查询？

**人话：SQL 执行太慢，用户等半天才看到结果**

```
正常查询：
  执行时间：0.01 秒
  用户体验：✅ 快

慢查询：
  执行时间：5 秒
  用户体验：❌ 慢，用户等得不耐烦
```

---

### 实际案例

```sql
-- 慢查询示例：查询订单列表
SELECT * FROM orders 
WHERE user_id = 1 
  AND status = 'paid' 
  AND create_time > '2024-01-01';

-- 问题：
-- 1. 表有 1000 万条数据
-- 2. 没有索引
-- 3. 执行时间：5 秒
```

---

### 详细流程图

```
┌─────────────────────────────────────────────────────────────┐
│                  慢查询问题完整流程                            │
└─────────────────────────────────────────────────────────────┘

步骤 1：用户发送请求
  ↓
  用户点击"我的订单"
  浏览器发送请求：GET /api/orders?userId=1

步骤 2：后端执行 SQL
  ↓
  后端代码：
    SELECT * FROM orders 
    WHERE user_id = 1 
      AND status = 'paid' 
      AND create_time > '2024-01-01';

步骤 3：MySQL 执行查询
  ↓
  MySQL 收到 SQL
  
  检查是否有索引：
    ❌ 没有索引
  
  执行方式：全表扫描
    ↓
    扫描第 1 条数据：user_id = 2，不匹配，跳过
    扫描第 2 条数据：user_id = 1，匹配！
    扫描第 3 条数据：user_id = 3，不匹配，跳过
    ...
    扫描第 1000 万条数据：user_id = 5，不匹配，跳过
  
  扫描完成：
    找到 100 条匹配的数据
    耗时：5 秒

步骤 4：返回结果
  ↓
  MySQL → 后端：100 条订单数据
  后端 → 前端：100 条订单数据

步骤 5：用户看到结果
  ↓
  用户等了 5 秒，终于看到订单列表
  用户体验：❌ 太慢了！

────────────────────────────────────────────────────────────

问题分析：
  1. 表有 1000 万条数据
  2. 没有索引，全表扫描
  3. 扫描 1000 万条数据，耗时 5 秒
```

---

### 解决方案：添加索引

```sql
-- 添加索引
CREATE INDEX idx_user_status_time 
ON orders(user_id, status, create_time);

-- 优化后的查询（SQL 不变）
SELECT * FROM orders 
WHERE user_id = 1 
  AND status = 'paid' 
  AND create_time > '2024-01-01';

-- 效果：
-- 执行时间：0.01 秒（从 5 秒降到 0.01 秒）
-- 性能提升：500 倍
```

---

### 优化后的流程图

```
┌─────────────────────────────────────────────────────────────┐
│                优化后的查询流程（有索引）                       │
└─────────────────────────────────────────────────────────────┘

步骤 1：用户发送请求
  ↓
  用户点击"我的订单"
  浏览器发送请求：GET /api/orders?userId=1

步骤 2：后端执行 SQL
  ↓
  后端代码：
    SELECT * FROM orders 
    WHERE user_id = 1 
      AND status = 'paid' 
      AND create_time > '2024-01-01';

步骤 3：MySQL 执行查询
  ↓
  MySQL 收到 SQL
  
  检查是否有索引：
    ✅ 有索引：idx_user_status_time
  
  执行方式：索引查询
    ↓
    通过索引快速定位：
      user_id = 1 的数据（1000 条）
      ↓
      在这 1000 条中，筛选 status = 'paid'（500 条）
      ↓
      在这 500 条中，筛选 create_time > '2024-01-01'（100 条）
  
  查询完成：
    找到 100 条匹配的数据
    耗时：0.01 秒

步骤 4：返回结果
  ↓
  MySQL → 后端：100 条订单数据
  后端 → 前端：100 条订单数据

步骤 5：用户看到结果
  ↓
  用户几乎立即看到订单列表
  用户体验：✅ 快！

────────────────────────────────────────────────────────────

优化效果：
  1. 有索引，不需要全表扫描
  2. 只扫描 1000 条数据（而不是 1000 万条）
  3. 耗时从 5 秒降到 0.01 秒
  4. 性能提升 500 倍
```

---

### 索引原理（简化版）

```
没有索引（全表扫描）：
  就像在一本没有目录的书里找内容
  需要从第 1 页翻到最后一页
  ❌ 慢

有索引：
  就像在一本有目录的书里找内容
  直接翻到目录，找到页码，跳到那一页
  ✅ 快

实际例子：
  表有 1000 万条数据
  
  没有索引：
    扫描 1000 万条数据
    耗时：5 秒
  
  有索引：
    通过索引快速定位
    只扫描 1000 条数据
    耗时：0.01 秒
```

---

## 问题 2：索引失效

### 什么是索引失效？

**人话：明明建了索引，但 MySQL 不用，还是全表扫描**

```
建了索引：
  CREATE INDEX idx_user_id ON orders(user_id);

但是 SQL 写得不对：
  SELECT * FROM orders WHERE user_id + 1 = 2;
  
结果：
  ❌ 索引失效，全表扫描
  ❌ 慢
```

---

### 7 种常见的索引失效场景

#### 场景 1：在索引列上使用函数

```sql
-- ❌ 错误写法（索引失效）
SELECT * FROM orders 
WHERE YEAR(create_time) = 2024;

-- 原因：
-- 在 create_time 列上使用了函数 YEAR()
-- MySQL 需要对每一行数据执行 YEAR() 函数
-- 无法使用索引

-- ✅ 正确写法（使用索引）
SELECT * FROM orders 
WHERE create_time >= '2024-01-01' 
  AND create_time < '2025-01-01';

-- 效果：
-- 不使用函数，直接比较
-- 可以使用索引
```

---

#### 场景 2：在索引列上使用运算

```sql
-- ❌ 错误写法（索引失效）
SELECT * FROM orders 
WHERE user_id + 1 = 2;

-- 原因：
-- 在 user_id 列上使用了运算 +1
-- MySQL 需要对每一行数据执行 +1 运算
-- 无法使用索引

-- ✅ 正确写法（使用索引）
SELECT * FROM orders 
WHERE user_id = 1;

-- 效果：
-- 不使用运算，直接比较
-- 可以使用索引
```

---

#### 场景 3：使用 LIKE 以 % 开头

```sql
-- ❌ 错误写法（索引失效）
SELECT * FROM users 
WHERE name LIKE '%张%';

-- 原因：
-- % 在开头，MySQL 不知道从哪里开始查找
-- 无法使用索引

-- ✅ 正确写法（使用索引）
SELECT * FROM users 
WHERE name LIKE '张%';

-- 效果：
-- % 在结尾，MySQL 知道从"张"开始查找
-- 可以使用索引
```

---

#### 场景 4：使用 OR 连接条件

```sql
-- ❌ 错误写法（索引失效）
SELECT * FROM orders 
WHERE user_id = 1 OR status = 'paid';

-- 原因：
-- OR 连接的条件，如果其中一个没有索引，整个查询都无法使用索引
-- 假设 user_id 有索引，但 status 没有索引
-- 整个查询无法使用索引

-- ✅ 正确写法（使用索引）
-- 方案 1：给两个字段都建索引
CREATE INDEX idx_user_id ON orders(user_id);
CREATE INDEX idx_status ON orders(status);

-- 方案 2：改用 UNION
SELECT * FROM orders WHERE user_id = 1
UNION
SELECT * FROM orders WHERE status = 'paid';
```

---

#### 场景 5：类型不匹配

```sql
-- ❌ 错误写法（索引失效）
SELECT * FROM orders 
WHERE user_id = '1';  -- user_id 是 INT 类型，但传了字符串

-- 原因：
-- user_id 是 INT 类型，但查询条件是字符串 '1'
-- MySQL 需要进行类型转换
-- 无法使用索引

-- ✅ 正确写法（使用索引）
SELECT * FROM orders 
WHERE user_id = 1;  -- 类型匹配

-- 效果：
-- 类型匹配，可以使用索引
```

---

#### 场景 6：使用 NOT、!=、<>

```sql
-- ❌ 错误写法（索引失效）
SELECT * FROM orders 
WHERE status != 'cancelled';

-- 原因：
-- 使用了 !=，MySQL 认为需要扫描大部分数据
-- 无法使用索引

-- ✅ 正确写法（使用索引）
SELECT * FROM orders 
WHERE status IN ('pending', 'paid', 'shipped');

-- 效果：
-- 使用 IN，可以使用索引
```

---

#### 场景 7：索引列顺序不对（联合索引）

```sql
-- 建立联合索引
CREATE INDEX idx_user_status_time 
ON orders(user_id, status, create_time);

-- ❌ 错误写法（索引失效）
SELECT * FROM orders 
WHERE status = 'paid' 
  AND create_time > '2024-01-01';

-- 原因：
-- 联合索引是 (user_id, status, create_time)
-- 但查询条件没有 user_id，直接从 status 开始
-- 违反了"最左前缀原则"
-- 无法使用索引

-- ✅ 正确写法（使用索引）
SELECT * FROM orders 
WHERE user_id = 1 
  AND status = 'paid' 
  AND create_time > '2024-01-01';

-- 效果：
-- 查询条件从 user_id 开始，符合"最左前缀原则"
-- 可以使用索引
```

---

### 最左前缀原则（重要）

```
联合索引：(user_id, status, create_time)

可以使用索引的查询：
  ✅ WHERE user_id = 1
  ✅ WHERE user_id = 1 AND status = 'paid'
  ✅ WHERE user_id = 1 AND status = 'paid' AND create_time > '2024-01-01'

无法使用索引的查询：
  ❌ WHERE status = 'paid'
  ❌ WHERE create_time > '2024-01-01'
  ❌ WHERE status = 'paid' AND create_time > '2024-01-01'

原因：
  联合索引就像电话簿：
    先按姓氏排序（user_id）
    再按名字排序（status）
    再按年龄排序（create_time）
  
  如果你不知道姓氏，直接找名字，就找不到了
```

---

## 问题 3：深分页

### 什么是深分页？

**人话：翻到第 10000 页，查询很慢**

```
浅分页（第 1 页）：
  SELECT * FROM orders LIMIT 0, 10;
  执行时间：0.01 秒
  ✅ 快

深分页（第 10000 页）：
  SELECT * FROM orders LIMIT 100000, 10;
  执行时间：5 秒
  ❌ 慢
```

---

### 实际案例

```sql
-- 深分页查询：查询第 10000 页的订单
SELECT * FROM orders 
ORDER BY id 
LIMIT 100000, 10;

-- 问题：
-- 1. MySQL 需要扫描 100010 条数据
-- 2. 然后丢弃前 100000 条
-- 3. 只返回最后 10 条
-- 4. 浪费资源，慢
```

---

### 详细流程图

```
┌─────────────────────────────────────────────────────────────┐
│                  深分页问题完整流程                            │
└─────────────────────────────────────────────────────────────┘

步骤 1：用户翻到第 10000 页
  ↓
  用户点击"第 10000 页"
  浏览器发送请求：GET /api/orders?page=10000&size=10

步骤 2：后端执行 SQL
  ↓
  后端代码：
    int offset = (page - 1) * size;  // (10000 - 1) * 10 = 99990
    SELECT * FROM orders 
    ORDER BY id 
    LIMIT 99990, 10;

步骤 3：MySQL 执行查询
  ↓
  MySQL 收到 SQL
  
  执行过程：
    ↓
    扫描第 1 条数据：id = 1
    扫描第 2 条数据：id = 2
    扫描第 3 条数据：id = 3
    ...
    扫描第 99990 条数据：id = 99990
    ↓
    丢弃前 99990 条数据（不返回）
    ↓
    扫描第 99991 条数据：id = 99991 ✅ 返回
    扫描第 99992 条数据：id = 99992 ✅ 返回
    ...
    扫描第 100000 条数据：id = 100000 ✅ 返回
  
  查询完成：
    扫描了 100000 条数据
    丢弃了 99990 条数据
    返回了 10 条数据
    耗时：5 秒

步骤 4：返回结果
  ↓
  MySQL → 后端：10 条订单数据
  后端 → 前端：10 条订单数据

步骤 5：用户看到结果
  ↓
  用户等了 5 秒，终于看到第 10000 页的订单
  用户体验：❌ 太慢了！

────────────────────────────────────────────────────────────

问题分析：
  1. 扫描了 100000 条数据
  2. 丢弃了 99990 条数据（浪费）
  3. 只返回了 10 条数据
  4. 耗时 5 秒
```

---

### 解决方案：使用子查询优化

```sql
-- ❌ 慢查询（深分页）
SELECT * FROM orders 
ORDER BY id 
LIMIT 100000, 10;

-- ✅ 优化后（使用子查询）
SELECT * FROM orders 
WHERE id >= (
    SELECT id FROM orders 
    ORDER BY id 
    LIMIT 100000, 1
)
ORDER BY id 
LIMIT 10;

-- 效果：
-- 执行时间：0.5 秒（从 5 秒降到 0.5 秒）
-- 性能提升：10 倍
```

---

### 优化后的流程图

```
┌─────────────────────────────────────────────────────────────┐
│              优化后的深分页查询流程                            │
└─────────────────────────────────────────────────────────────┘

步骤 1：执行子查询（只查询 id）
  ↓
  SELECT id FROM orders 
  ORDER BY id 
  LIMIT 100000, 1;
  
  执行过程：
    ↓
    通过索引快速定位到第 100000 条数据
    只返回 id（不返回其他字段）
    耗时：0.1 秒
  
  结果：id = 100000

步骤 2：执行主查询
  ↓
  SELECT * FROM orders 
  WHERE id >= 100000 
  ORDER BY id 
  LIMIT 10;
  
  执行过程：
    ↓
    通过索引快速定位到 id >= 100000 的数据
    只扫描 10 条数据
    耗时：0.01 秒
  
  结果：10 条订单数据

步骤 3：返回结果
  ↓
  总耗时：0.1 + 0.01 = 0.11 秒
  性能提升：5 / 0.11 = 45 倍

────────────────────────────────────────────────────────────

优化原理：
  1. 子查询只查询 id（不查询其他字段）
  2. 通过索引快速定位
  3. 主查询只扫描 10 条数据
  4. 不需要扫描和丢弃 99990 条数据
```

---

## 问题 4：N+1 查询

### 什么是 N+1 查询？

**人话：查询 1 次主表，然后循环查询 N 次子表，总共查询 N+1 次**

```
场景：查询订单列表，每个订单显示用户信息

N+1 查询：
  1. 查询订单列表（1 次）
  2. 循环查询每个订单的用户信息（N 次）
  总共：N+1 次查询
  ❌ 慢

优化后：
  1. 查询订单列表（1 次）
  2. 一次性查询所有用户信息（1 次）
  总共：2 次查询
  ✅ 快
```

---

### 实际案例

```java
// ❌ N+1 查询（慢）
// 查询订单列表
List<Order> orders = orderMapper.selectList();  // 1 次查询

// 循环查询每个订单的用户信息
for (Order order : orders) {
    User user = userMapper.selectById(order.getUserId());  // N 次查询
    order.setUser(user);
}

// 问题：
// 如果有 100 个订单，总共查询 101 次（1 + 100）
// 每次查询耗时 0.01 秒，总耗时 1.01 秒
```

---

### 详细流程图

```
┌─────────────────────────────────────────────────────────────┐
│                  N+1 查询问题完整流程                          │
└─────────────────────────────────────────────────────────────┘

步骤 1：查询订单列表
  ↓
  SQL：SELECT * FROM orders LIMIT 100;
  耗时：0.01 秒
  
  结果：100 个订单
    订单 1：id=1, user_id=10
    订单 2：id=2, user_id=20
    订单 3：id=3, user_id=30
    ...
    订单 100：id=100, user_id=1000

步骤 2：循环查询用户信息
  ↓
  查询订单 1 的用户信息：
    SQL：SELECT * FROM users WHERE id = 10;
    耗时：0.01 秒
  
  查询订单 2 的用户信息：
    SQL：SELECT * FROM users WHERE id = 20;
    耗时：0.01 秒
  
  查询订单 3 的用户信息：
    SQL：SELECT * FROM users WHERE id = 30;
    耗时：0.01 秒
  
  ...
  
  查询订单 100 的用户信息：
    SQL：SELECT * FROM users WHERE id = 1000;
    耗时：0.01 秒

步骤 3：返回结果
  ↓
  总查询次数：101 次（1 + 100）
  总耗时：1.01 秒（0.01 + 100 * 0.01）
  
  用户体验：❌ 慢

────────────────────────────────────────────────────────────

问题分析：
  1. 查询了 101 次数据库
  2. 每次查询都有网络开销
  3. 总耗时 1.01 秒
```

---

### 解决方案：使用 JOIN 或 IN 查询

```java
// ✅ 优化方案 1：使用 JOIN（推荐）
// 一次性查询订单和用户信息
List<Order> orders = orderMapper.selectOrdersWithUser();

// SQL：
SELECT o.*, u.* 
FROM orders o 
LEFT JOIN users u ON o.user_id = u.id 
LIMIT 100;

// 效果：
// 只查询 1 次数据库
// 耗时：0.02 秒
```

```java
// ✅ 优化方案 2：使用 IN 查询
// 1. 查询订单列表
List<Order> orders = orderMapper.selectList();  // 1 次查询

// 2. 提取所有 user_id
List<Long> userIds = orders.stream()
    .map(Order::getUserId)
    .collect(Collectors.toList());

// 3. 一次性查询所有用户信息
List<User> users = userMapper.selectByIds(userIds);  // 1 次查询

// SQL：
SELECT * FROM users WHERE id IN (10, 20, 30, ..., 1000);

// 4. 组装数据
Map<Long, User> userMap = users.stream()
    .collect(Collectors.toMap(User::getId, u -> u));

for (Order order : orders) {
    order.setUser(userMap.get(order.getUserId()));
}

// 效果：
// 只查询 2 次数据库
// 耗时：0.02 秒
```

---

### 优化后的流程图

```
┌─────────────────────────────────────────────────────────────┐
│              优化后的查询流程（使用 JOIN）                     │
└─────────────────────────────────────────────────────────────┘

步骤 1：一次性查询订单和用户信息
  ↓
  SQL：
    SELECT o.*, u.* 
    FROM orders o 
    LEFT JOIN users u ON o.user_id = u.id 
    LIMIT 100;
  
  执行过程：
    ↓
    MySQL 同时查询 orders 表和 users 表
    通过 user_id 关联
    一次性返回所有数据
  
  耗时：0.02 秒
  
  结果：100 个订单 + 100 个用户信息
    订单 1：id=1, user_id=10, user_name=张三
    订单 2：id=2, user_id=20, user_name=李四
    ...

步骤 2：返回结果
  ↓
  总查询次数：1 次
  总耗时：0.02 秒
  
  用户体验：✅ 快

────────────────────────────────────────────────────────────

优化效果：
  1. 查询次数：从 101 次降到 1 次
  2. 耗时：从 1.01 秒降到 0.02 秒
  3. 性能提升：50 倍
```

---

## 问题 5：大表查询

### 什么是大表查询？

**人话：表有几千万甚至上亿条数据，查询很慢**

```
小表（100 万条数据）：
  查询速度：快
  ✅ 没问题

大表（1 亿条数据）：
  查询速度：慢
  ❌ 有问题
```

---

### 实际案例

```sql
-- 大表查询：查询订单列表
SELECT * FROM orders 
WHERE create_time > '2024-01-01';

-- 问题：
-- 1. 表有 1 亿条数据
-- 2. 符合条件的有 5000 万条数据
-- 3. 查询很慢，甚至超时
```

---

### 解决方案：分库分表

```
方案 1：垂直分表
  把一张大表拆分成多张小表
  
  原表：orders（100 个字段）
    ↓
  拆分后：
    orders_base（10 个常用字段）
    orders_detail（90 个不常用字段）
  
  效果：
    查询常用字段时，只查询 orders_base
    速度快

方案 2：水平分表
  把一张大表的数据拆分到多张表
  
  原表：orders（1 亿条数据）
    ↓
  拆分后：
    orders_2024_01（1000 万条数据）
    orders_2024_02（1000 万条数据）
    ...
    orders_2024_12（1000 万条数据）
  
  效果：
    查询 2024 年 1 月的订单，只查询 orders_2024_01
    速度快

方案 3：分库分表
  把数据拆分到多个数据库
  
  原库：db_orders（1 亿条数据）
    ↓
  拆分后：
    db_orders_1（2500 万条数据）
    db_orders_2（2500 万条数据）
    db_orders_3（2500 万条数据）
    db_orders_4（2500 万条数据）
  
  效果：
    查询分散到 4 个数据库
    速度快
```

---

## SQL 优化完整流程

### 流程图

```
┌─────────────────────────────────────────────────────────────┐
│                  SQL 优化完整流程                              │
└─────────────────────────────────────────────────────────────┘

步骤 1：发现慢查询
  ↓
  方式 1：用户反馈（页面加载慢）
  方式 2：监控告警（接口响应时间超过 1 秒）
  方式 3：慢查询日志（MySQL 慢查询日志）

步骤 2：定位慢查询 SQL
  ↓
  查看慢查询日志：
    /var/log/mysql/slow.log
  
  找到慢查询 SQL：
    SELECT * FROM orders 
    WHERE user_id = 1 
      AND status = 'paid' 
      AND create_time > '2024-01-01';
    
    执行时间：5 秒

步骤 3：分析 SQL 执行计划
  ↓
  使用 EXPLAIN 分析：
    EXPLAIN SELECT * FROM orders 
    WHERE user_id = 1 
      AND status = 'paid' 
      AND create_time > '2024-01-01';
  
  结果：
    type: ALL（全表扫描）
    rows: 10000000（扫描 1000 万行）
    Extra: Using where
  
  问题：全表扫描，没有使用索引

步骤 4：优化 SQL
  ↓
  方案 1：添加索引
    CREATE INDEX idx_user_status_time 
    ON orders(user_id, status, create_time);
  
  方案 2：优化 SQL 写法
    避免索引失效（不使用函数、运算等）
  
  方案 3：使用覆盖索引
    只查询需要的字段，不查询 *
  
  方案 4：分页优化
    使用子查询优化深分页
  
  方案 5：避免 N+1 查询
    使用 JOIN 或 IN 查询

步骤 5：验证优化效果
  ↓
  再次执行 EXPLAIN：
    EXPLAIN SELECT * FROM orders 
    WHERE user_id = 1 
      AND status = 'paid' 
      AND create_time > '2024-01-01';
  
  结果：
    type: ref（使用索引）
    rows: 1000（只扫描 1000 行）
    Extra: Using index condition
  
  效果：使用索引，性能提升

步骤 6：测试性能
  ↓
  执行优化后的 SQL：
    SELECT * FROM orders 
    WHERE user_id = 1 
      AND status = 'paid' 
      AND create_time > '2024-01-01';
  
  执行时间：0.01 秒（从 5 秒降到 0.01 秒）
  性能提升：500 倍

步骤 7：上线验证
  ↓
  部署到生产环境
  监控接口响应时间
  
  结果：
    接口响应时间：从 5 秒降到 0.01 秒
    用户体验：✅ 快
    优化成功！

────────────────────────────────────────────────────────────

优化流程总结：
  1. 发现慢查询
  2. 定位慢查询 SQL
  3. 分析 SQL 执行计划（EXPLAIN）
  4. 优化 SQL（添加索引、优化写法）
  5. 验证优化效果（EXPLAIN）
  6. 测试性能
  7. 上线验证
```

---

## EXPLAIN 详解

### 什么是 EXPLAIN？

**人话：EXPLAIN 就像 SQL 的"体检报告"，告诉你 SQL 是怎么执行的**

```
使用方式：
  EXPLAIN SELECT * FROM orders WHERE user_id = 1;

作用：
  1. 查看 SQL 是否使用索引
  2. 查看扫描了多少行数据
  3. 查看执行计划
```

---

### EXPLAIN 结果详解

```sql
EXPLAIN SELECT * FROM orders WHERE user_id = 1;

-- 结果：
+----+-------------+--------+------+---------------+------+---------+------+------+-------------+
| id | select_type | table  | type | possible_keys | key  | key_len | ref  | rows | Extra       |
+----+-------------+--------+------+---------------+------+---------+------+------+-------------+
|  1 | SIMPLE      | orders | ALL  | NULL          | NULL | NULL    | NULL | 1000 | Using where |
+----+-------------+--------+------+---------------+------+---------+------+------+-------------+
```

---

### 重要字段说明

#### 1. type（最重要）

```
type 表示访问类型，性能从好到坏：

system > const > eq_ref > ref > range > index > ALL

✅ system：表只有一行数据（最快）
✅ const：通过主键或唯一索引查询（很快）
✅ eq_ref：唯一索引查询（快）
✅ ref：非唯一索引查询（快）
⚠️ range：范围查询（中等）
⚠️ index：索引全扫描（慢）
❌ ALL：全表扫描（最慢）

实际例子：
  type = const：
    SELECT * FROM orders WHERE id = 1;
    通过主键查询，最快
  
  type = ref：
    SELECT * FROM orders WHERE user_id = 1;
    通过索引查询，快
  
  type = range：
    SELECT * FROM orders WHERE create_time > '2024-01-01';
    范围查询，中等
  
  type = ALL：
    SELECT * FROM orders WHERE status = 'paid';
    没有索引，全表扫描，最慢
```

---

#### 2. key（使用的索引）

```
key 表示实际使用的索引

NULL：没有使用索引（❌ 慢）
idx_user_id：使用了 idx_user_id 索引（✅ 快）

实际例子：
  key = NULL：
    没有使用索引，全表扫描
    ❌ 需要优化
  
  key = idx_user_id：
    使用了 idx_user_id 索引
    ✅ 好
```

---

#### 3. rows（扫描行数）

```
rows 表示预计扫描的行数

rows = 10：扫描 10 行（✅ 快）
rows = 1000：扫描 1000 行（⚠️ 中等）
rows = 1000000：扫描 100 万行（❌ 慢）

实际例子：
  rows = 1：
    通过主键查询，只扫描 1 行
    ✅ 最快
  
  rows = 1000：
    通过索引查询，扫描 1000 行
    ⚠️ 中等
  
  rows = 10000000：
    全表扫描，扫描 1000 万行
    ❌ 最慢，需要优化
```

---

#### 4. Extra（额外信息）

```
Extra 表示额外信息

✅ Using index：使用覆盖索引（最快）
✅ Using index condition：使用索引条件下推（快）
⚠️ Using where：使用 WHERE 过滤（中等）
⚠️ Using filesort：使用文件排序（慢）
❌ Using temporary：使用临时表（最慢）

实际例子：
  Extra = Using index：
    SELECT id, user_id FROM orders WHERE user_id = 1;
    只查询索引列，不需要回表
    ✅ 最快
  
  Extra = Using where：
    SELECT * FROM orders WHERE user_id = 1;
    使用 WHERE 过滤
    ⚠️ 中等
  
  Extra = Using filesort：
    SELECT * FROM orders ORDER BY create_time;
    没有索引，需要排序
    ❌ 慢，需要优化
```

---

## 实际优化案例

### 案例 1：订单列表查询优化

```sql
-- 原始 SQL（慢）
SELECT * FROM orders 
WHERE user_id = 1 
  AND status = 'paid' 
  AND create_time > '2024-01-01'
ORDER BY create_time DESC
LIMIT 10;

-- 执行时间：5 秒
```

**步骤 1：分析执行计划**

```sql
EXPLAIN SELECT * FROM orders 
WHERE user_id = 1 
  AND status = 'paid' 
  AND create_time > '2024-01-01'
ORDER BY create_time DESC
LIMIT 10;

-- 结果：
type: ALL（全表扫描）
key: NULL（没有使用索引）
rows: 10000000（扫描 1000 万行）
Extra: Using where; Using filesort（需要排序）

-- 问题：
1. 全表扫描
2. 没有使用索引
3. 需要排序
```

**步骤 2：添加索引**

```sql
-- 添加联合索引
CREATE INDEX idx_user_status_time 
ON orders(user_id, status, create_time);
```

**步骤 3：再次分析执行计划**

```sql
EXPLAIN SELECT * FROM orders 
WHERE user_id = 1 
  AND status = 'paid' 
  AND create_time > '2024-01-01'
ORDER BY create_time DESC
LIMIT 10;

-- 结果：
type: ref（使用索引）
key: idx_user_status_time（使用了索引）
rows: 1000（只扫描 1000 行）
Extra: Using index condition（使用索引条件）

-- 效果：
1. 使用索引
2. 只扫描 1000 行
3. 不需要排序（索引已排序）
```

**步骤 4：测试性能**

```sql
SELECT * FROM orders 
WHERE user_id = 1 
  AND status = 'paid' 
  AND create_time > '2024-01-01'
ORDER BY create_time DESC
LIMIT 10;

-- 执行时间：0.01 秒（从 5 秒降到 0.01 秒）
-- 性能提升：500 倍
```

---

### 案例 2：深分页优化

```sql
-- 原始 SQL（慢）
SELECT * FROM orders 
ORDER BY id 
LIMIT 100000, 10;

-- 执行时间：5 秒
```

**步骤 1：分析执行计划**

```sql
EXPLAIN SELECT * FROM orders 
ORDER BY id 
LIMIT 100000, 10;

-- 结果：
type: index（索引全扫描）
key: PRIMARY（使用主键索引）
rows: 100010（扫描 100010 行）
Extra: NULL

-- 问题：
1. 需要扫描 100010 行
2. 丢弃前 100000 行
3. 浪费资源
```

**步骤 2：优化 SQL**

```sql
-- 优化后的 SQL
SELECT * FROM orders 
WHERE id >= (
    SELECT id FROM orders 
    ORDER BY id 
    LIMIT 100000, 1
)
ORDER BY id 
LIMIT 10;
```

**步骤 3：再次分析执行计划**

```sql
EXPLAIN SELECT * FROM orders 
WHERE id >= (
    SELECT id FROM orders 
    ORDER BY id 
    LIMIT 100000, 1
)
ORDER BY id 
LIMIT 10;

-- 结果：
-- 子查询：
type: index
key: PRIMARY
rows: 100001（扫描 100001 行，但只返回 id）

-- 主查询：
type: range
key: PRIMARY
rows: 10（只扫描 10 行）

-- 效果：
1. 子查询只返回 id（不返回其他字段）
2. 主查询只扫描 10 行
3. 不需要扫描和丢弃 100000 行
```

**步骤 4：测试性能**

```sql
SELECT * FROM orders 
WHERE id >= (
    SELECT id FROM orders 
    ORDER BY id 
    LIMIT 100000, 1
)
ORDER BY id 
LIMIT 10;

-- 执行时间：0.5 秒（从 5 秒降到 0.5 秒）
-- 性能提升：10 倍
```

---

### 案例 3：N+1 查询优化

```java
// 原始代码（慢）
List<Order> orders = orderMapper.selectList();  // 1 次查询

for (Order order : orders) {
    User user = userMapper.selectById(order.getUserId());  // N 次查询
    order.setUser(user);
}

// 总查询次数：101 次（1 + 100）
// 总耗时：1.01 秒
```

**步骤 1：分析问题**

```
问题：
  1. 查询了 101 次数据库
  2. 每次查询都有网络开销
  3. 总耗时 1.01 秒
```

**步骤 2：优化代码**

```java
// 优化后的代码（使用 JOIN）
List<Order> orders = orderMapper.selectOrdersWithUser();  // 1 次查询

// SQL：
SELECT o.*, u.* 
FROM orders o 
LEFT JOIN users u ON o.user_id = u.id 
LIMIT 100;

// 总查询次数：1 次
// 总耗时：0.02 秒
```

**步骤 3：测试性能**

```
优化前：
  查询次数：101 次
  耗时：1.01 秒

优化后：
  查询次数：1 次
  耗时：0.02 秒

性能提升：50 倍
```

---

## SQL 优化技巧总结

### 1. 索引优化

```
✅ 给常用的查询字段添加索引
✅ 使用联合索引（注意最左前缀原则）
✅ 避免索引失效（不使用函数、运算、LIKE %开头）
✅ 定期分析索引使用情况，删除无用索引
```

---

### 2. SQL 写法优化

```
✅ 只查询需要的字段（不使用 SELECT *）
✅ 避免在 WHERE 子句中使用函数
✅ 避免在 WHERE 子句中使用运算
✅ 使用 LIMIT 限制返回数据量
✅ 使用 JOIN 代替子查询（大部分情况）
```

---

### 3. 分页优化

```
✅ 浅分页：直接使用 LIMIT
✅ 深分页：使用子查询优化
✅ 使用游标分页（记录上次查询的最后一条数据的 id）
```

---

### 4. 避免 N+1 查询

```
✅ 使用 JOIN 一次性查询
✅ 使用 IN 查询批量获取数据
✅ 使用缓存减少数据库查询
```

---

### 5. 大表优化

```
✅ 垂直分表（拆分字段）
✅ 水平分表（拆分数据）
✅ 分库分表（拆分到多个数据库）
✅ 使用归档表（历史数据归档）
```

---

## 一句话总结

**SQL 优化的核心思路：**

1. **发现慢查询**：通过监控、日志、用户反馈
2. **分析执行计划**：使用 EXPLAIN 查看 SQL 执行情况
3. **添加索引**：给常用查询字段添加索引
4. **优化 SQL 写法**：避免索引失效、避免 N+1 查询
5. **验证效果**：再次使用 EXPLAIN 验证，测试性能
6. **上线验证**：部署到生产环境，监控效果

**记住：索引是 SQL 优化的核心，90% 的慢查询都可以通过添加索引解决！**

