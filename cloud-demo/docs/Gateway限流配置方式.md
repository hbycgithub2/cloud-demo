# Gateway 限流配置方式

## 一、两种配置方式

### 方式 1：配置文件配置（推荐）✅

**优点：**
- ✅ 简单，不需要写代码
- ✅ 修改方便，改配置文件就行
- ✅ 不需要重启服务（配合 Nacos）

**缺点：**
- ⚠️ 功能有限，只能做简单限流

---

### 方式 2：代码配置

**优点：**
- ✅ 功能强大，可以做复杂限流
- ✅ 灵活，想怎么限流就怎么限流

**缺点：**
- ❌ 需要写代码
- ❌ 修改麻烦，要重新编译部署

---

## 二、方式 1：配置文件配置（推荐）

### 步骤 1：添加依赖

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Spring Cloud Gateway -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    
    <!-- Redis（限流需要 Redis）-->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>
</dependencies>
```

---

### 步骤 2：配置 Redis

```yaml
# application.yml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
```

---

### 步骤 3：配置限流规则

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        # UserService 路由
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
          filters:
            # 限流过滤器
            - name: RequestRateLimiter
              args:
                # 限流规则：每秒最多 10 个请求
                redis-rate-limiter.replenishRate: 10      # 每秒生成 10 个令牌
                redis-rate-limiter.burstCapacity: 20      # 令牌桶容量 20 个
                redis-rate-limiter.requestedTokens: 1     # 每个请求消耗 1 个令牌
                # 限流 key（根据什么限流）
                key-resolver: "#{@ipKeyResolver}"         # 根据 IP 限流
```

**配置说明：**

```yaml
redis-rate-limiter.replenishRate: 10
  ↓
  每秒生成 10 个令牌
  就是每秒最多 10 个请求

redis-rate-limiter.burstCapacity: 20
  ↓
  令牌桶容量 20 个
  可以应对突发流量（短时间内可以处理 20 个请求）

redis-rate-limiter.requestedTokens: 1
  ↓
  每个请求消耗 1 个令牌

key-resolver: "#{@ipKeyResolver}"
  ↓
  根据 IP 限流
  每个 IP 每秒最多 10 个请求
```

---

### 步骤 4：定义 KeyResolver（限流 key）

```java
// KeyResolverConfig.java
@Configuration
public class KeyResolverConfig {
    
    // 方案 1：根据 IP 限流（推荐）
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // 获取客户端 IP
            String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }
    
    // 方案 2：根据用户 ID 限流
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // 从 Token 获取用户 ID
            String userId = getUserIdFromToken(exchange);
            return Mono.just(userId);
        };
    }
    
    // 方案 3：根据接口路径限流
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            // 获取请求路径
            String path = exchange.getRequest().getPath().value();
            return Mono.just(path);
        };
    }
}
```

**KeyResolver 说明：**

```
KeyResolver 的作用：决定根据什么限流

方案 1：根据 IP 限流
  - key = IP 地址（例如：192.168.1.100）
  - 每个 IP 每秒最多 10 个请求
  - 适用场景：防止单个 IP 恶意刷接口

方案 2：根据用户 ID 限流
  - key = 用户 ID（例如：1）
  - 每个用户每秒最多 10 个请求
  - 适用场景：防止单个用户恶意刷接口

方案 3：根据接口路径限流
  - key = 接口路径（例如：/user/1）
  - 每个接口每秒最多 10 个请求
  - 适用场景：保护单个接口
```

---

### 完整配置示例

```yaml
# application.yml
spring:
  # Redis 配置
  redis:
    host: localhost
    port: 6379
    
  cloud:
    gateway:
      routes:
        # UserService 路由 + 限流
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10    # 每秒 10 个请求
                redis-rate-limiter.burstCapacity: 20    # 令牌桶容量 20
                redis-rate-limiter.requestedTokens: 1   # 每个请求消耗 1 个令牌
                key-resolver: "#{@ipKeyResolver}"       # 根据 IP 限流
        
        # OrderService 路由 + 限流
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5     # 每秒 5 个请求
                redis-rate-limiter.burstCapacity: 10    # 令牌桶容量 10
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@ipKeyResolver}"
```

```java
// KeyResolverConfig.java
@Configuration
public class KeyResolverConfig {
    
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress()
                .getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }
}
```

**就这么简单！**

---

## 三、方式 2：代码配置

### 方案 1：写一个过滤器（最灵活）

```java
// UserServiceRateLimitFilter.java
@Component
public class UserServiceRateLimitFilter implements GlobalFilter, Ordered {
    
    // 限流：每秒最多 10 个请求
    private final RateLimiter rateLimiter = RateLimiter.create(10);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 只限流 /user/** 路径
        if (path.startsWith("/user/")) {
            // 尝试获取令牌
            if (!rateLimiter.tryAcquire()) {
                // 没有令牌，拒绝
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }
        
        // 有令牌，放行
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -1;  // 优先级最高
    }
}
```

**优点：**
- ✅ 灵活，想怎么限流就怎么限流
- ✅ 不需要 Redis

**缺点：**
- ❌ 需要写代码
- ❌ 单机限流（多个 Gateway 不共享）

---

### 方案 2：使用 Redis 实现分布式限流

```java
// RedisRateLimitFilter.java
@Component
public class RedisRateLimitFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final int MAX_REQUESTS = 10;  // 每秒最多 10 个请求
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 只限流 /user/** 路径
        if (!path.startsWith("/user/")) {
            return chain.filter(exchange);
        }
        
        // 1. 生成 key
        String currentSecond = String.valueOf(System.currentTimeMillis() / 1000);
        String key = "rate_limit:user:" + currentSecond;
        
        // 2. 计数器 +1
        Long count = redisTemplate.opsForValue().increment(key);
        
        // 3. 设置过期时间（2 秒后自动删除）
        if (count == 1) {
            redisTemplate.expire(key, 2, TimeUnit.SECONDS);
        }
        
        // 4. 检查是否超过限制
        if (count > MAX_REQUESTS) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -1;
    }
}
```

**优点：**
- ✅ 分布式限流（多个 Gateway 共享）
- ✅ 灵活

**缺点：**
- ❌ 需要写代码
- ❌ 需要 Redis

---

## 四、对比表

| 对比项 | 配置文件配置 | 代码配置 |
|--------|-------------|---------|
| 难度 | ⭐ 简单 | ⭐⭐⭐ 复杂 |
| 代码量 | 0 行（只需要配置文件）| 50 行 |
| 灵活性 | ⚠️ 功能有限 | ✅ 功能强大 |
| 修改方便 | ✅ 改配置文件就行 | ❌ 要重新编译部署 |
| 分布式 | ✅ 支持（用 Redis）| ✅ 支持（用 Redis）|
| 推荐 | ✅ 推荐（简单场景）| ⚠️ 复杂场景才用 |

---

## 五、推荐方案

### 小白推荐：配置文件配置 ✅

**为什么推荐：**
- ✅ 简单，不需要写代码
- ✅ 修改方便
- ✅ 够用了（90% 的场景）

**完整步骤：**

```
步骤 1：添加依赖
  - spring-cloud-starter-gateway
  - spring-boot-starter-data-redis-reactive

步骤 2：配置 Redis
  - application.yml 里配置 Redis 地址

步骤 3：配置限流规则
  - application.yml 里配置限流参数

步骤 4：定义 KeyResolver
  - 写一个 @Bean，决定根据什么限流（IP、用户 ID、路径）

完成！
```

---

### 高级用户推荐：代码配置

**什么时候用：**
- 需要复杂的限流逻辑
- 需要根据不同条件限流
- 需要自定义限流算法

**示例：**

```java
// 复杂限流逻辑
@Component
public class AdvancedRateLimitFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String userId = getUserIdFromToken(exchange);
        
        // 根据不同条件限流
        if (path.startsWith("/user/login")) {
            // 登录接口：每秒 10 个请求
            return checkRateLimit(exchange, chain, "login", 10);
        } else if (path.startsWith("/user/list")) {
            // 查询列表：每秒 100 个请求
            return checkRateLimit(exchange, chain, "list", 100);
        } else if (isVipUser(userId)) {
            // VIP 用户：每秒 100 个请求
            return checkRateLimit(exchange, chain, "vip:" + userId, 100);
        } else {
            // 普通用户：每秒 10 个请求
            return checkRateLimit(exchange, chain, "normal:" + userId, 10);
        }
    }
    
    private Mono<Void> checkRateLimit(ServerWebExchange exchange, 
                                       GatewayFilterChain chain, 
                                       String key, 
                                       int maxRequests) {
        // 限流逻辑
        // ...
    }
}
```

---

## 六、实际项目配置示例

### 配置文件方式（推荐）

```yaml
# application.yml
spring:
  redis:
    host: localhost
    port: 6379
    
  cloud:
    gateway:
      routes:
        # UserService：每秒 10 个请求
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"
        
        # OrderService：每秒 5 个请求
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
                key-resolver: "#{@ipKeyResolver}"
```

```java
// KeyResolverConfig.java
@Configuration
public class KeyResolverConfig {
    
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress()
                .getAddress().getHostAddress()
        );
    }
}
```

---

## 七、常见问题

### Q1：配置文件配置和代码配置哪个好？

**A：看场景**

```
简单场景（90% 的项目）：
  → 用配置文件配置 ✅
  → 简单，够用

复杂场景（10% 的项目）：
  → 用代码配置
  → 灵活，功能强大
```

---

### Q2：配置文件配置需要 Redis 吗？

**A：需要**

```
配置文件配置：
  - 必须用 Redis
  - 因为 Spring Cloud Gateway 的限流是基于 Redis 的

代码配置：
  - 可以不用 Redis（单机限流）
  - 也可以用 Redis（分布式限流）
```

---

### Q3：怎么修改限流规则？

**A：看配置方式**

```
配置文件配置：
  - 修改 application.yml
  - 重启服务（或配合 Nacos 动态刷新）

代码配置：
  - 修改代码
  - 重新编译部署
```

---

## 八、总结

### 两种配置方式

```
方式 1：配置文件配置（推荐）✅
  - 简单，不需要写代码
  - 修改方便
  - 够用了（90% 的场景）
  - 需要 Redis

方式 2：代码配置
  - 灵活，功能强大
  - 需要写代码
  - 复杂场景才用
```

### 推荐方案

```
小白推荐：配置文件配置 ✅
  - 只需要 3 步：
    1. 添加依赖
    2. 配置 Redis
    3. 配置限流规则
  - 不需要写代码

高级用户：代码配置
  - 需要复杂限流逻辑时才用
```

### 一句话总结

**Gateway 限流有两种方式：配置文件配置（推荐，简单，不需要写代码，只需要在 application.yml 里配置限流规则）和代码配置（灵活，需要写过滤器代码）。90% 的项目用配置文件配置就够了，只需要 3 步：添加依赖 → 配置 Redis → 配置限流规则。复杂场景才用代码配置！**
