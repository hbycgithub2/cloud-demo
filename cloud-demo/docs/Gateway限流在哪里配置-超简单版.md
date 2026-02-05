# Gateway 限流在哪里配置 - 超简单版

## 一、直接回答

**Gateway 限流有两个地方可以配置：**

```
方式 1：配置文件（application.yml）✅ 推荐
  - 在 application.yml 里配置
  - 不需要写代码
  - 90% 的项目用这个

方式 2：代码（写过滤器）
  - 写一个 Java 类（过滤器）
  - 需要写代码
  - 10% 的项目用这个（复杂场景）
```

---

## 二、方式 1：配置文件配置（推荐）

### 在哪里配置？

```
文件位置：
cloud-demo/gateway/src/main/resources/application.yml
```

### 怎么配置？

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
          filters:
            # ⬇️ 就是这里！限流配置
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10    # 每秒 10 个请求
                redis-rate-limiter.burstCapacity: 20    # 令牌桶容量 20
                key-resolver: "#{@ipKeyResolver}"       # 根据 IP 限流
```

**就这么简单！在配置文件里加几行就行了！**

---

### 完整示例（可以直接复制）

```yaml
# application.yml
server:
  port: 10010
  
spring:
  application:
    name: gateway
    
  # ⬇️ 第 1 步：配置 Redis（限流需要 Redis）
  redis:
    host: localhost
    port: 6379
    
  cloud:
    nacos:
      server-addr: localhost:8848
      
    gateway:
      routes:
        # ⬇️ 第 2 步：给 UserService 配置限流
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
          filters:
            # 限流配置
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10    # 每秒 10 个请求
                redis-rate-limiter.burstCapacity: 20    # 令牌桶容量 20
                key-resolver: "#{@ipKeyResolver}"       # 根据 IP 限流
        
        # OrderService（不限流）
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
```

**然后还需要一个 Java 类（定义 KeyResolver）：**

```java
// 文件位置：gateway/src/main/java/cn/itcast/gateway/KeyResolverConfig.java

package cn.itcast.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {
    
    // ⬇️ 第 3 步：定义 KeyResolver（根据 IP 限流）
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // 获取客户端 IP
            String ip = exchange.getRequest().getRemoteAddress()
                .getAddress().getHostAddress();
            return Mono.just(ip);
        };
    }
}
```

**完成！就这 3 步：**

```
步骤 1：配置 Redis（application.yml）
步骤 2：配置限流规则（application.yml）
步骤 3：定义 KeyResolver（写一个 Java 类）
```

---

## 三、方式 2：代码配置

### 在哪里配置？

```
写一个 Java 类（过滤器）

文件位置：
cloud-demo/gateway/src/main/java/cn/itcast/gateway/RateLimitFilter.java
```

### 怎么配置？

```java
// RateLimitFilter.java
package cn.itcast.gateway;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    // ⬇️ 限流：每秒最多 10 个请求
    private final RateLimiter rateLimiter = RateLimiter.create(10);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // ⬇️ 只限流 /user/** 路径
        if (path.startsWith("/user/")) {
            // 尝试获取令牌
            if (!rateLimiter.tryAcquire()) {
                // 没有令牌，拒绝请求
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

**就这么简单！写一个过滤器就行了！**

---

## 四、两种方式对比

| 对比项 | 配置文件 | 代码 |
|--------|---------|------|
| **在哪里配置** | application.yml | 写一个 Java 类 |
| **需要写代码吗** | ❌ 不需要（只需要一个 KeyResolver 类）| ✅ 需要 |
| **代码量** | 10 行（KeyResolver）| 50 行（过滤器）|
| **难度** | ⭐ 简单 | ⭐⭐⭐ 复杂 |
| **修改方便吗** | ✅ 改配置文件就行 | ❌ 要重新编译部署 |
| **灵活性** | ⚠️ 功能有限 | ✅ 功能强大 |
| **推荐** | ✅ 推荐（90% 的项目）| ⚠️ 复杂场景才用 |

---

## 五、你的项目现在是怎么配置的？

### 查看你的 application.yml

```yaml
# cloud-demo/gateway/src/main/resources/application.yml

server:
  port: 10010
spring:
  application:
    name: gateway
  cloud:
    nacos:
      server-addr: localhost:8848
    gateway:
      routes:
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
```

**结论：你的项目现在没有配置限流！**

```
你的 application.yml 里：
  ✅ 有路由配置（routes）
  ❌ 没有限流配置（RequestRateLimiter）
  ❌ 没有 Redis 配置

你的代码里：
  ❌ 没有限流过滤器（RateLimitFilter）
  ✅ 只有一个鉴权过滤器（AuthorizeFilter，已禁用）
```

---

## 六、如果你想加限流，怎么做？

### 方案 1：配置文件配置（推荐）✅

**步骤 1：修改 application.yml**

```yaml
# cloud-demo/gateway/src/main/resources/application.yml

server:
  port: 10010
  
spring:
  application:
    name: gateway
    
  # ⬇️ 新增：配置 Redis
  redis:
    host: localhost
    port: 6379
    
  cloud:
    nacos:
      server-addr: localhost:8848
      
    gateway:
      routes:
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
          # ⬇️ 新增：限流配置
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10    # 每秒 10 个请求
                redis-rate-limiter.burstCapacity: 20    # 令牌桶容量 20
                key-resolver: "#{@ipKeyResolver}"       # 根据 IP 限流
                
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
```

**步骤 2：创建 KeyResolverConfig.java**

```java
// 文件位置：gateway/src/main/java/cn/itcast/gateway/KeyResolverConfig.java

package cn.itcast.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

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

**步骤 3：添加依赖（pom.xml）**

```xml
<!-- gateway/pom.xml -->
<dependencies>
    <!-- 已有的依赖 -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    
    <!-- ⬇️ 新增：Redis 依赖（限流需要）-->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>
</dependencies>
```

**完成！重启 Gateway 就生效了！**

---

### 方案 2：代码配置

**创建 RateLimitFilter.java**

```java
// 文件位置：gateway/src/main/java/cn/itcast/gateway/RateLimitFilter.java

package cn.itcast.gateway;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {
    
    // 限流：每秒最多 10 个请求
    private final RateLimiter rateLimiter = RateLimiter.create(10);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 只限流 /user/** 路径
        if (path.startsWith("/user/")) {
            if (!rateLimiter.tryAcquire()) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -1;
    }
}
```

**完成！重启 Gateway 就生效了！**

---

## 七、流程图：两种配置方式

### 方式 1：配置文件配置

```
┌─────────────────────────────────────────────────────────────┐
│                     配置文件配置流程                          │
└─────────────────────────────────────────────────────────────┘

第 1 步：修改 application.yml
  ↓
  添加 Redis 配置
  添加限流配置（RequestRateLimiter）

第 2 步：创建 KeyResolverConfig.java
  ↓
  定义 @Bean ipKeyResolver()
  决定根据什么限流（IP、用户 ID、路径）

第 3 步：添加依赖（pom.xml）
  ↓
  添加 spring-boot-starter-data-redis-reactive

第 4 步：重启 Gateway
  ↓
  ✅ 限流生效！

────────────────────────────────────────────────────────────

优点：
  ✅ 简单，只需要改配置文件
  ✅ 不需要写太多代码（只需要一个 KeyResolver 类）
  ✅ 修改方便（改配置文件就行）

缺点：
  ⚠️ 功能有限（只能做简单限流）
  ⚠️ 需要 Redis
```

---

### 方式 2：代码配置

```
┌─────────────────────────────────────────────────────────────┐
│                      代码配置流程                             │
└─────────────────────────────────────────────────────────────┘

第 1 步：创建 RateLimitFilter.java
  ↓
  实现 GlobalFilter 接口
  写限流逻辑（tryAcquire）

第 2 步：重启 Gateway
  ↓
  ✅ 限流生效！

────────────────────────────────────────────────────────────

优点：
  ✅ 灵活，想怎么限流就怎么限流
  ✅ 不需要 Redis（单机限流）

缺点：
  ❌ 需要写代码
  ❌ 修改麻烦（要重新编译部署）
  ⚠️ 单机限流（多个 Gateway 不共享）
```

---

## 八、实际运行流程

### 配置文件配置的运行流程

```
┌─────────────────────────────────────────────────────────────┐
│              配置文件配置的限流运行流程                        │
└─────────────────────────────────────────────────────────────┘

客户端发送请求
  ↓
  http://localhost:10010/user/1

Gateway 收到请求
  ↓
  读取 application.yml 配置
  发现 /user/** 路径配置了限流

执行 RequestRateLimiter 过滤器
  ↓
  1. 调用 KeyResolver 获取 key
     key = ipKeyResolver.resolve(exchange)
     key = "192.168.1.100"（客户端 IP）
  
  2. 从 Redis 获取令牌
     key = "request_rate_limiter.{192.168.1.100}.tokens"
     tokens = redis.get(key)  // 当前令牌数
  
  3. 检查是否有令牌
     if (tokens > 0) {
         // 有令牌，扣除 1 个令牌
         redis.decr(key)
         ✅ 放行
     } else {
         // 没有令牌
         ❌ 拒绝（返回 429 Too Many Requests）
     }

如果放行
  ↓
  转发到 UserService
  http://localhost:8081/user/1

返回响应
  ↓
  客户端收到响应

────────────────────────────────────────────────────────────

关键点：
  1. 配置在 application.yml 里
  2. 限流逻辑由 Spring Cloud Gateway 自动执行
  3. 令牌存储在 Redis 里
  4. KeyResolver 决定根据什么限流（IP、用户 ID、路径）
```

---

### 代码配置的运行流程

```
┌─────────────────────────────────────────────────────────────┐
│               代码配置的限流运行流程                          │
└─────────────────────────────────────────────────────────────┘

客户端发送请求
  ↓
  http://localhost:10010/user/1

Gateway 收到请求
  ↓
  执行所有 GlobalFilter

执行 RateLimitFilter
  ↓
  1. 检查路径
     path = "/user/1"
     if (path.startsWith("/user/")) {
         // 需要限流
     }
  
  2. 尝试获取令牌
     boolean acquired = rateLimiter.tryAcquire()
     
     if (acquired) {
         // 有令牌
         ✅ 放行
     } else {
         // 没有令牌
         ❌ 拒绝（返回 429 Too Many Requests）
     }

如果放行
  ↓
  转发到 UserService
  http://localhost:8081/user/1

返回响应
  ↓
  客户端收到响应

────────────────────────────────────────────────────────────

关键点：
  1. 配置在代码里（RateLimitFilter.java）
  2. 限流逻辑由你自己写
  3. 令牌存储在内存里（Guava RateLimiter）
  4. 灵活，想怎么限流就怎么限流
```

---

## 九、一句话总结

**Gateway 限流有两个地方配置：**

```
方式 1：配置文件（application.yml）✅ 推荐
  - 在 application.yml 里配置限流规则
  - 写一个 KeyResolver 类（10 行代码）
  - 简单，够用，90% 的项目用这个

方式 2：代码（写过滤器）
  - 写一个 RateLimitFilter 类（50 行代码）
  - 灵活，功能强大
  - 复杂场景才用（10% 的项目）
```

**你的项目现在没有配置限流，如果想加限流，推荐用配置文件方式（简单，够用）！**

