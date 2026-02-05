# HikariCP 连接池详解

## 一、HikariCP 是什么

**HikariCP** 是目前 Java 生态中**性能最好的数据库连接池**，Spring Boot 2.x 默认使用。

### 核心作用

```
应用程序
    ↓
HikariCP 连接池（管理数据库连接）
    ↓
MySQL 数据库
```

**解决的问题：**
- 避免频繁创建/销毁数据库连接（耗时操作）
- 复用连接，提升性能
- 控制并发连接数，保护数据库

---

## 二、为什么需要连接池

### 没有连接池的情况

```java
// 每次查询都创建新连接
Connection conn = DriverManager.getConnection(url, user, password);  // 耗时 50-100ms
PreparedStatement ps = conn.prepareStatement(sql);
ResultSet rs = ps.executeQuery();  // 查询耗时 1ms
rs.close();
ps.close();
conn.close();  // 关闭连接
```

**问题：**
- 创建连接耗时 50-100ms
- 查询只需 1ms
- 大部分时间浪费在连接创建上
- 高并发时数据库连接数暴增，数据库崩溃

---

### 使用连接池的情况

```java
// 从连接池获取连接（已创建好的）
Connection conn = dataSource.getConnection();  // 耗时 <1ms
PreparedStatement ps = conn.prepareStatement(sql);
ResultSet rs = ps.executeQuery();  // 查询耗时 1ms
rs.close();
ps.close();
conn.close();  // 归还连接到池中，不是真正关闭
```

**优势：**
- 获取连接耗时 <1ms
- 连接复用，性能提升 50-100 倍
- 控制最大连接数，保护数据库

---

## 三、HikariCP 工作原理

### 连接池生命周期

```
① 应用启动
   ↓
② HikariCP 初始化
   创建 minimumIdle 个连接（默认 10 个）
   ↓
③ 应用运行中
   - 请求到达 → 从池中获取连接
   - 查询完成 → 归还连接到池中
   - 连接不够 → 创建新连接（最多 maximumPoolSize 个）
   - 连接空闲 → 保持 minimumIdle 个，多余的关闭
   ↓
④ 应用关闭
   关闭所有连接
```

---

### 连接池状态图

```
连接池 (maximumPoolSize = 10)
┌─────────────────────────────────────┐
│ [活跃连接] [活跃连接] [活跃连接]    │  ← 正在被使用
│ [空闲连接] [空闲连接] [空闲连接]    │  ← 等待被使用
│ [空闲连接] [空闲连接] [空闲连接]    │
│ [空闲连接]                          │
└─────────────────────────────────────┘
  ↑                              ↑
  minimumIdle = 5          maximumPoolSize = 10
```

---

## 四、HikariCP 配置详解

### 当前项目配置（默认）

```yaml
spring:
  datasource:
    url: jdbc:p6spy:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    # HikariCP 使用默认配置
```

**默认配置：**
- `maximumPoolSize`: 10（最大连接数）
- `minimumIdle`: 10（最小空闲连接数）
- `connectionTimeout`: 30000ms（获取连接超时时间）
- `idleTimeout`: 600000ms（空闲连接超时时间，10分钟）
- `maxLifetime`: 1800000ms（连接最大存活时间，30分钟）

---

### 完整配置示例

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    
    # HikariCP 配置
    hikari:
      # 连接池名称
      pool-name: UserServiceHikariCP
      
      # 最小空闲连接数（推荐与 maximum-pool-size 相同）
      minimum-idle: 10
      
      # 最大连接数（核心配置）
      maximum-pool-size: 20
      
      # 获取连接超时时间（毫秒）
      connection-timeout: 30000
      
      # 空闲连接超时时间（毫秒）
      idle-timeout: 600000
      
      # 连接最大存活时间（毫秒）
      max-lifetime: 1800000
      
      # 连接测试查询（MySQL 推荐不配置，使用 JDBC4 的 isValid()）
      # connection-test-query: SELECT 1
      
      # 自动提交
      auto-commit: true
      
      # 连接初始化 SQL
      # connection-init-sql: SET NAMES utf8mb4
```

---

## 五、高并发场景配置

### 场景1：低并发（QPS < 100）

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 10
      connection-timeout: 30000
```

**说明：**
- 连接数少，节省资源
- 适合小型应用

---

### 场景2：中等并发（QPS 100-1000）

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 10
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**说明：**
- 平衡性能和资源
- 适合大部分应用

---

### 场景3：高并发（QPS > 1000）

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 50
      maximum-pool-size: 50
      connection-timeout: 10000
      idle-timeout: 300000
      max-lifetime: 1800000
      
      # 性能优化配置
      leak-detection-threshold: 60000  # 连接泄漏检测（60秒）
```

**说明：**
- `minimum-idle = maximum-pool-size`：避免动态创建连接的开销
- 连接数多，支持高并发
- 缩短超时时间，快速失败

---

### 场景4：超高并发（QPS > 5000）

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 100
      maximum-pool-size: 100
      connection-timeout: 5000
      idle-timeout: 300000
      max-lifetime: 1800000
      
      # 性能优化
      leak-detection-threshold: 60000
      
      # 数据库配置优化
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        useLocalSessionState: true
        rewriteBatchedStatements: true
        cacheResultSetMetadata: true
        cacheServerConfiguration: true
        elideSetAutoCommits: true
        maintainTimeStats: false
```

**说明：**
- 连接数 100+
- 启用 PreparedStatement 缓存
- 优化 MySQL 驱动参数

---

## 六、连接池大小计算公式

### 官方推荐公式

```
connections = ((core_count * 2) + effective_spindle_count)
```

**参数说明：**
- `core_count`: CPU 核心数
- `effective_spindle_count`: 磁盘数（机械硬盘为实际数量，SSD 为 1）

**示例：**
- 4 核 CPU + SSD：`(4 * 2) + 1 = 9`
- 8 核 CPU + SSD：`(8 * 2) + 1 = 17`
- 16 核 CPU + SSD：`(16 * 2) + 1 = 33`

---

### 实际经验值

```
maximum-pool-size = (并发请求数 / 单次请求耗时) * 1.2
```

**示例：**
- 并发 100 请求/秒
- 单次查询 10ms
- 计算：`(100 / 100) * 1.2 = 1.2` → 设置为 10（留余量）

**注意：**
- 不是越大越好
- 过多连接会增加数据库负担
- 一般不超过 100

---

## 七、高并发优化策略

### 1. 连接池配置优化

```yaml
spring:
  datasource:
    hikari:
      # 固定连接数，避免动态创建
      minimum-idle: 50
      maximum-pool-size: 50
      
      # 缩短超时时间
      connection-timeout: 5000
      
      # 连接泄漏检测
      leak-detection-threshold: 60000
```

---

### 2. 数据库连接参数优化

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cloud_user?useSSL=false&useUnicode=true&characterEncoding=utf8&autoReconnect=true&failOverReadOnly=false&maxReconnects=10
    hikari:
      data-source-properties:
        # 启用 PreparedStatement 缓存
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        
        # 批量操作优化
        rewriteBatchedStatements: true
        
        # 减少网络往返
        useLocalSessionState: true
        cacheServerConfiguration: true
```

---

### 3. 读写分离

```yaml
spring:
  datasource:
    # 主库（写）
    master:
      url: jdbc:mysql://master:3306/cloud_user
      hikari:
        maximum-pool-size: 20
    
    # 从库（读）
    slave:
      url: jdbc:mysql://slave:3306/cloud_user
      hikari:
        maximum-pool-size: 50
```

---

### 4. 多数据源

```yaml
spring:
  datasource:
    # 用户库
    user:
      url: jdbc:mysql://localhost:3306/cloud_user
      hikari:
        maximum-pool-size: 30
    
    # 订单库
    order:
      url: jdbc:mysql://localhost:3306/cloud_order
      hikari:
        maximum-pool-size: 50
```

---

## 八、监控和调优

### 1. 启用 HikariCP 监控

```yaml
spring:
  datasource:
    hikari:
      # 注册 JMX MBean
      register-mbeans: true
```

**查看指标：**
- 活跃连接数
- 空闲连接数
- 等待连接的线程数
- 连接创建/销毁次数

---

### 2. 使用 Actuator 监控

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

**访问：**
```
http://localhost:8081/actuator/metrics/hikaricp.connections.active
http://localhost:8081/actuator/metrics/hikaricp.connections.idle
http://localhost:8081/actuator/metrics/hikaricp.connections.pending
```

---

### 3. 日志监控

```yaml
logging:
  level:
    com.zaxxer.hikari: DEBUG
```

**输出示例：**
```
HikariPool-1 - Pool stats (total=10, active=3, idle=7, waiting=0)
```

---

## 九、常见问题

### 1. 连接池耗尽

**现象：**
```
java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.
```

**原因：**
- 并发请求过多
- 连接泄漏（未关闭连接）
- 数据库响应慢

**解决：**
```yaml
spring:
  datasource:
    hikari:
      # 增加连接数
      maximum-pool-size: 50
      
      # 启用连接泄漏检测
      leak-detection-threshold: 60000
```

---

### 2. 连接泄漏

**现象：**
```
Connection leak detection triggered for connection
```

**原因：**
- 代码中未关闭连接
- 异常导致连接未释放

**解决：**
```java
// 使用 try-with-resources 自动关闭
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql);
     ResultSet rs = ps.executeQuery()) {
    // 处理结果
}
```

---

### 3. 连接超时

**现象：**
```
The last packet successfully received from the server was X milliseconds ago.
```

**原因：**
- MySQL `wait_timeout` 默认 8 小时
- 连接空闲超过此时间被 MySQL 关闭

**解决：**
```yaml
spring:
  datasource:
    hikari:
      # 连接最大存活时间小于 MySQL wait_timeout
      max-lifetime: 1800000  # 30分钟
      
      # 定期测试连接
      keepalive-time: 300000  # 5分钟
```

---

## 十、性能对比

### HikariCP vs 其他连接池

| 连接池 | 获取连接耗时 | 内存占用 | 性能 |
|--------|-------------|---------|------|
| HikariCP | **0.1ms** | **低** | **最快** |
| Druid | 0.3ms | 中 | 快 |
| C3P0 | 1.0ms | 高 | 慢 |
| DBCP2 | 0.5ms | 中 | 中 |

**HikariCP 优势：**
- 字节码级别优化
- 无锁设计
- 连接代理优化
- 内存占用最小

---

## 十一、最佳实践

### 1. 连接池配置

```yaml
spring:
  datasource:
    hikari:
      # 固定连接数（推荐）
      minimum-idle: 20
      maximum-pool-size: 20
      
      # 合理的超时时间
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      
      # 连接泄漏检测
      leak-detection-threshold: 60000
```

---

### 2. 代码规范

```java
// ✅ 正确：使用 try-with-resources
try (Connection conn = dataSource.getConnection()) {
    // 使用连接
}

// ❌ 错误：手动关闭容易遗漏
Connection conn = dataSource.getConnection();
try {
    // 使用连接
} finally {
    conn.close();  // 可能因异常未执行
}
```

---

### 3. 监控告警

- 监控活跃连接数
- 监控等待连接的线程数
- 连接数超过 80% 时告警
- 连接泄漏时告警

---

## 十二、总结

### 核心要点

1. **HikariCP 是什么**：高性能数据库连接池
2. **为什么需要**：复用连接，提升性能 50-100 倍
3. **如何配置**：根据并发量调整 `maximum-pool-size`
4. **高并发优化**：固定连接数、启用缓存、读写分离
5. **监控调优**：使用 Actuator 监控，启用连接泄漏检测

### 推荐配置（中等并发）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
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

## 十三、相关文件

- `application.yml`: `user-service/src/main/resources/application.yml`
- HikariCP 官方文档: https://github.com/brettwooldridge/HikariCP
- Spring Boot 数据源配置: https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#application-properties.data
