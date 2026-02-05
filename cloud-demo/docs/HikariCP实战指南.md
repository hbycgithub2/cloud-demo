# HikariCP 连接池实战指南

## 一、项目中如何体现使用了 HikariCP

### 1. 依赖体现（隐式引入）

**pom.xml 中的依赖：**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

**依赖传递关系：**
```
spring-boot-starter-web
  └─ spring-boot-starter
      └─ spring-boot-starter-jdbc
          └─ HikariCP (默认连接池)
```

**Spring Boot 2.x 默认使用 HikariCP**，无需显式添加依赖。

---

### 2. 启动日志体现

启动 UserApplication 时，控制台会输出：

```
2026-01-18 21:45:30.123  INFO 12345 --- [main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-01-18 21:45:30.456  INFO 12345 --- [main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
```

**关键信息：**
- `HikariPool-1`：连接池名称
- `Starting...`：连接池正在初始化
- `Start completed`：连接池启动完成

---

### 3. 代码体现（自动注入）

```java
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;  // MyBatis 自动使用 HikariCP
    
    public User queryById(Long id) {
        // 底层流程：
        // 1. MyBatis 从 HikariCP 获取连接
        // 2. 执行 SQL
        // 3. 归还连接到 HikariCP
        return userMapper.findById(id);
    }
}
```

**你看不到 HikariCP 的代码，但它在后台工作：**
```
UserService.queryById()
    ↓
MyBatis 执行 SQL
    ↓
从 HikariCP 获取连接 (dataSource.getConnection())
    ↓
执行查询
    ↓
归还连接到 HikariCP (connection.close())
```

---

### 4. 监控端点体现

访问 Actuator 端点（需要添加依赖）：
```
http://localhost:8081/actuator/metrics/hikaricp.connections.active
```

返回：
```json
{
  "name": "hikaricp.connections.active",
  "measurements": [
    {
      "statistic": "VALUE",
      "value": 3.0
    }
  ]
}
```

---

## 二、连接池配置在哪里

### 当前项目配置（使用默认值）

**文件位置：** `user-service/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:p6spy:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    # 没有配置 hikari，使用默认值
```

**默认配置：**
- `maximum-pool-size`: 10
- `minimum-idle`: 10
- `connection-timeout`: 30000ms (30秒)
- `idle-timeout`: 600000ms (10分钟)
- `max-lifetime`: 1800000ms (30分钟)

---

### 如何添加 HikariCP 配置

**在 application.yml 中添加：**

```yaml
spring:
  datasource:
    url: jdbc:p6spy:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    
    # HikariCP 配置
    hikari:
      pool-name: UserServiceHikariCP
      minimum-idle: 20
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

---

## 三、配置参数根据什么配置

### 核心配置参数详解

| 参数 | 默认值 | 推荐值 | 依据 |
|------|--------|--------|------|
| `maximum-pool-size` | 10 | 20-50 | **并发请求数** |
| `minimum-idle` | 10 | = maximum | **避免动态创建** |
| `connection-timeout` | 30000ms | 30000ms | **用户等待时间** |
| `idle-timeout` | 600000ms | 600000ms | **连接复用时间** |
| `max-lifetime` | 1800000ms | 1800000ms | **MySQL wait_timeout** |

---

### 1. maximum-pool-size（最大连接数）

**依据：并发请求数**

#### 计算公式

```
maximum-pool-size = 并发请求数 × 单次请求数据库耗时 × 安全系数

示例：
- 并发 100 请求/秒
- 单次查询 10ms (0.01秒)
- 安全系数 1.5

计算：100 × 0.01 × 1.5 = 1.5 → 设置为 10（留余量）
```

#### 官方推荐公式

```
connections = (CPU核心数 × 2) + 磁盘数

示例：
- 4核CPU + SSD：(4 × 2) + 1 = 9 → 设置为 10
- 8核CPU + SSD：(8 × 2) + 1 = 17 → 设置为 20
```

#### 实际场景

| 场景 | QPS | 推荐值 | 原因 |
|------|-----|--------|------|
| 低并发 | < 100 | 10 | 节省资源 |
| 中等并发 | 100-1000 | 20 | 平衡性能 |
| 高并发 | 1000-5000 | 50 | 支持高并发 |
| 超高并发 | > 5000 | 100 | 极限性能 |

---

### 2. minimum-idle（最小空闲连接数）

**依据：避免动态创建连接的开销**

#### 推荐配置

```yaml
hikari:
  minimum-idle: 20
  maximum-pool-size: 20  # 与 minimum-idle 相同
```

**原因：**
- 动态创建连接耗时 50-100ms
- 固定连接数，性能更稳定
- 高并发场景必须这样配置

#### 对比

| 配置 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| `minimum-idle < maximum` | 节省资源 | 动态创建有延迟 | 低并发 |
| `minimum-idle = maximum` | 性能稳定 | 占用资源多 | 高并发 |

---

### 3. connection-timeout（获取连接超时）

**依据：用户等待时间**

```yaml
hikari:
  connection-timeout: 30000  # 30秒
```

**原因：**
- 30秒是用户可接受的等待时间
- 超过 30秒，用户会认为系统卡死
- 高并发场景可缩短到 5-10秒，快速失败

---

### 4. max-lifetime（连接最大存活时间）

**依据：MySQL wait_timeout**

```yaml
hikari:
  max-lifetime: 1800000  # 30分钟
```

**原因：**
- MySQL 默认 `wait_timeout = 8小时`
- 连接空闲超过此时间，MySQL 会关闭连接
- HikariCP 的 `max-lifetime` 必须小于 MySQL 的 `wait_timeout`
- 推荐设置为 30分钟，定期刷新连接

---

## 四、高并发场景配置

### 场景1：电商秒杀（QPS 5000+）

**业务特点：**
- 瞬时并发极高
- 查询简单（主键查询）
- 响应时间要求 < 100ms

**配置：**
```yaml
spring:
  datasource:
    hikari:
      pool-name: SeckillHikariCP
      minimum-idle: 100
      maximum-pool-size: 100
      connection-timeout: 5000
      idle-timeout: 300000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      
      # MySQL 驱动优化
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        rewriteBatchedStatements: true
```

**为什么这么配置：**
1. **100 个连接**：支持 5000+ QPS（单次查询 10ms，100 × 100 = 10000 QPS）
2. **固定连接数**：避免动态创建，性能稳定
3. **5秒超时**：快速失败，避免雪崩
4. **启用缓存**：PreparedStatement 缓存，减少解析开销

---

### 场景2：订单查询（QPS 1000）

**业务特点：**
- 并发稳定
- 查询复杂（多表关联）
- 响应时间要求 < 500ms

**配置：**
```yaml
spring:
  datasource:
    hikari:
      pool-name: OrderHikariCP
      minimum-idle: 30
      maximum-pool-size: 30
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

**为什么这么配置：**
1. **30 个连接**：1000 QPS × 0.03秒 = 30 个连接
2. **30秒超时**：复杂查询可能较慢，给足时间
3. **连接泄漏检测**：防止连接未释放

---

### 场景3：报表统计（QPS 10）

**业务特点：**
- 并发低
- 查询极慢（大数据量聚合）
- 响应时间要求 < 10秒

**配置：**
```yaml
spring:
  datasource:
    hikari:
      pool-name: ReportHikariCP
      minimum-idle: 5
      maximum-pool-size: 10
      connection-timeout: 60000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**为什么这么配置：**
1. **10 个连接**：并发低，节省资源
2. **60秒超时**：慢查询需要更长时间
3. **动态连接**：`minimum-idle < maximum`，按需创建

---

## 五、真实问题案例

### 案例1：连接池耗尽导致服务不可用

#### 问题现象

```
2026-01-18 10:30:15.123 ERROR 12345 --- [http-nio-8081-exec-50] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] threw exception

java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.
```

**用户反馈：**
- 页面一直转圈，30秒后报错
- 高峰期（上午10点）频繁出现
- 重启服务后恢复，但很快又出现

---

#### 问题分析

**1. 查看监控数据：**
```
活跃连接数：10/10 (100%)
等待连接的线程：50+
```

**2. 查看配置：**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # 太小了！
```

**3. 查看业务数据：**
- 高峰期 QPS：500
- 单次查询耗时：50ms
- 需要连接数：500 × 0.05 = 25

**结论：** 连接数不足，10 个连接无法支撑 500 QPS

---

#### 解决方案

**修改配置：**
```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 30
      maximum-pool-size: 30
      connection-timeout: 30000
      leak-detection-threshold: 60000
```

**优化 SQL：**
```sql
-- 优化前：50ms
SELECT * FROM tb_order WHERE user_id = ?

-- 优化后：5ms（添加索引）
CREATE INDEX idx_user_id ON tb_order(user_id);
SELECT * FROM tb_order WHERE user_id = ?
```

**效果：**
- 连接数从 10 → 30
- 查询耗时从 50ms → 5ms
- 需要连接数：500 × 0.005 = 2.5（30 个连接绰绰有余）
- 问题解决，高峰期稳定运行

---

### 案例2：连接泄漏导致连接数逐渐减少

#### 问题现象

```
2026-01-18 14:20:30.456 WARN 12345 --- [HikariPool-1 housekeeper] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Connection leak detection triggered for connection com.mysql.cj.jdbc.ConnectionImpl@7a5d012c on thread http-nio-8081-exec-23, stack trace follows

java.lang.Exception: Apparent connection leak detected
```

**用户反馈：**
- 服务运行一段时间后变慢
- 重启后恢复，但几小时后又变慢
- 活跃连接数持续增加，不释放

---

#### 问题分析

**1. 查看代码：**
```java
// ❌ 错误代码
public List<User> queryUsers() {
    Connection conn = dataSource.getConnection();
    PreparedStatement ps = conn.prepareStatement(sql);
    ResultSet rs = ps.executeQuery();
    
    List<User> users = new ArrayList<>();
    while (rs.next()) {
        users.add(mapUser(rs));
    }
    
    // 忘记关闭连接！
    return users;
}
```

**2. 查看监控：**
```
活跃连接数：10/10 (100%)
空闲连接数：0
连接泄漏告警：每小时 50+ 次
```

**结论：** 代码中未关闭连接，导致连接泄漏

---

#### 解决方案

**修改代码：**
```java
// ✅ 正确代码
public List<User> queryUsers() {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
        
        List<User> users = new ArrayList<>();
        while (rs.next()) {
            users.add(mapUser(rs));
        }
        return users;
        
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
    // try-with-resources 自动关闭连接
}
```

**启用连接泄漏检测：**
```yaml
spring:
  datasource:
    hikari:
      leak-detection-threshold: 60000  # 60秒
```

**效果：**
- 连接正常释放
- 活跃连接数稳定在 2-5 个
- 无连接泄漏告警
- 问题解决

---

### 案例3：数据库连接超时

#### 问题现象

```
2026-01-18 03:15:20.789 ERROR 12345 --- [http-nio-8081-exec-10] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() threw exception

com.mysql.cj.jdbc.exceptions.CommunicationsException: The last packet successfully received from the server was 28,800,123 milliseconds ago. The last packet sent successfully to the server was 28,800,123 milliseconds ago.
```

**用户反馈：**
- 凌晨 3 点左右报错
- 白天正常，夜间无流量时出现
- 28800 秒 = 8 小时

---

#### 问题分析

**1. MySQL 配置：**
```sql
SHOW VARIABLES LIKE 'wait_timeout';
-- wait_timeout = 28800 (8小时)
```

**2. HikariCP 配置：**
```yaml
spring:
  datasource:
    hikari:
      max-lifetime: 1800000  # 30分钟
```

**3. 问题原因：**
- 夜间无流量，连接空闲超过 8 小时
- MySQL 主动关闭连接
- HikariCP 不知道连接已关闭
- 下次使用时报错

---

#### 解决方案

**方案1：缩短连接存活时间**
```yaml
spring:
  datasource:
    hikari:
      max-lifetime: 1800000  # 30分钟（小于 MySQL 的 8 小时）
      keepalive-time: 300000  # 5分钟心跳检测
```

**方案2：增加 MySQL wait_timeout**
```sql
SET GLOBAL wait_timeout = 86400;  -- 24小时
```

**推荐方案1**，因为：
- 定期刷新连接更健康
- 避免长时间持有连接
- 不依赖 MySQL 配置

**效果：**
- 连接每 30 分钟刷新一次
- 每 5 分钟心跳检测
- 无超时报错
- 问题解决

---

## 六、最佳实践总结

### 1. 配置原则

```yaml
spring:
  datasource:
    hikari:
      # 原则1：固定连接数（高并发）
      minimum-idle: 30
      maximum-pool-size: 30
      
      # 原则2：合理超时时间
      connection-timeout: 30000
      
      # 原则3：定期刷新连接
      max-lifetime: 1800000
      keepalive-time: 300000
      
      # 原则4：启用连接泄漏检测
      leak-detection-threshold: 60000
```

---

### 2. 代码规范

```java
// ✅ 推荐：使用 MyBatis/JPA，自动管理连接
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    public User queryById(Long id) {
        return userMapper.findById(id);
    }
}

// ✅ 如果必须手动管理，使用 try-with-resources
try (Connection conn = dataSource.getConnection()) {
    // 使用连接
}

// ❌ 禁止：手动关闭容易遗漏
Connection conn = dataSource.getConnection();
try {
    // 使用连接
} finally {
    conn.close();
}
```

---

### 3. 监控告警

**监控指标：**
- 活跃连接数
- 空闲连接数
- 等待连接的线程数
- 连接创建/销毁次数

**告警规则：**
- 活跃连接数 > 80%：扩容
- 等待线程数 > 10：连接数不足
- 连接泄漏检测触发：代码问题

---

### 4. 压测验证

**压测步骤：**
1. 使用 JMeter/Gatling 模拟并发
2. 监控连接池指标
3. 调整配置参数
4. 重复测试，找到最优值

**示例：**
```bash
# 模拟 1000 并发
jmeter -n -t test.jmx -l result.jtl -Jthreads=1000
```

---

## 七、快速参考

### 不同场景推荐配置

| 场景 | QPS | maximum-pool-size | 原因 |
|------|-----|-------------------|------|
| 低并发 | < 100 | 10 | 节省资源 |
| 中等并发 | 100-1000 | 20-30 | 平衡性能 |
| 高并发 | 1000-5000 | 50 | 支持高并发 |
| 超高并发 | > 5000 | 100 | 极限性能 |
| 秒杀活动 | 10000+ | 100-200 | 瞬时高峰 |

### 配置模板

```yaml
# 中等并发（推荐）
spring:
  datasource:
    hikari:
      pool-name: MyHikariCP
      minimum-idle: 20
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      keepalive-time: 300000
      leak-detection-threshold: 60000
```

---

## 八、总结

### 核心要点

1. **HikariCP 在哪**：Spring Boot 2.x 默认使用，启动日志可见
2. **配置在哪**：`application.yml` 的 `spring.datasource.hikari`
3. **如何配置**：根据 QPS、查询耗时、CPU 核心数计算
4. **高并发配置**：固定连接数、启用缓存、连接泄漏检测
5. **真实案例**：连接池耗尽、连接泄漏、连接超时

### 一句话总结

**HikariCP 通过连接复用提升性能 50-100 倍，高并发场景必须固定连接数（minimum-idle = maximum-pool-size）并启用连接泄漏检测。**
