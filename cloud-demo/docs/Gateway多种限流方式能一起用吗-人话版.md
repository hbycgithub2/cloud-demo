# Gateway 多种限流方式能一起用吗 - 人话版

## 一、直接回答

**配置文件方式：不能直接一起用（只能选一个 KeyResolver）❌**

**代码方式：可以一起用（写多个过滤器）✅**

---

## 二、为什么配置文件方式不能一起用？

### 问题：只能配置一个 KeyResolver

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
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                key-resolver: "#{@ipKeyResolver}"  # ⬅️ 只能写一个！
```

**问题：**
```
key-resolver: "#{@ipKeyResolver}"
  ↓
  只能写一个 KeyResolver
  不能写多个

❌ 不能这样写：
key-resolver: "#{@ipKeyResolver, @userKeyResolver, @pathKeyResolver}"
```

---

### 如果你定义了多个 KeyResolver 会怎么样？

```java
@Configuration
public class KeyResolverConfig {
    
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(getIp(exchange));
    }
    
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(getUserId(exchange));
    }
    
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(getPath(exchange));
    }
}
```

**结果：**
```
配置文件里只能选一个：
  key-resolver: "#{@ipKeyResolver}"      ← 只能用 IP 限流
  或
  key-resolver: "#{@userKeyResolver}"    ← 只能用用户 ID 限流
  或
  key-resolver: "#{@pathKeyResolver}"    ← 只能用路径限流

不能同时用多个！
```

---

## 三、解决方案：怎么实现多种限流方式一起用？

### 方案 1：组合 KeyResolver（推荐）✅

**思路：把多个条件组合成一个 key**

```java
@Configuration
public class KeyResolverConfig {
    
    // 组合限流：IP + 用户 ID + 路径
    @Bean
    public KeyResolver combinedKeyResolver() {
        return exchange -> {
            // 1. 获取 IP
            String ip = exchange.getRequest().getRemoteAddress()
                .getAddress().getHostAddress();
            
            // 2. 获取用户 ID（从 Token）
            String userId = getUserIdFromToken(exchange);
            
            // 3. 获取路径
            String path = exchange.getRequest().getPath().value();
            
            // 4. 组合成一个 key
            String key = ip + ":" + userId + ":" + path;
            // 例如：192.168.1.100:1:/user/1
            
            return Mono.just(key);
        };
    }
    
    private String getUserIdFromToken(ServerWebExchange exchange) {
        // 从 Token 解析用户 ID
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token != null) {
            // 解析 Token，获取 userId
            return parseUserId(token);
        }
        return "anonymous";
    }
}
```

**配置：**
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
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                key-resolver: "#{@combinedKeyResolver}"  # 使用组合 KeyResolver
```

**效果：**
```
限流 key = IP + 用户 ID + 路径
  例如：192.168.1.100:1:/user/1

这样就实现了：
  ✅ 同一个 IP 的不同用户，分别限流
  ✅ 同一个用户的不同路径，分别限流
  ✅ 同一个路径的不同 IP，分别限流
```

---

### 方案 2：写多个过滤器（最灵活）✅

**思路：写多个过滤器，每个过滤器负责一种限流**

```java
// 过滤器 1：根据 IP 限流
@Component
@Order(-3)
public class IpRateLimitFilter implements GlobalFilter, Ordered {
    
    private final Map<String, RateLimiter> ipLimiters = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress()
            .getAddress().getHostAddress();
        
        // 每个 IP 每秒最多 100 个请求
        RateLimiter limiter = ipLimiters.computeIfAbsent(ip, 
            k -> RateLimiter.create(100));
        
        if (!limiter.tryAcquire()) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -3;
    }
}

// 过滤器 2：根据用户 ID 限流
@Component
@Order(-2)
public class UserRateLimitFilter implements GlobalFilter, Ordered {
    
    private final Map<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = getUserIdFromToken(exchange);
        
        // 每个用户每秒最多 10 个请求
        RateLimiter limiter = userLimiters.computeIfAbsent(userId, 
            k -> RateLimiter.create(10));
        
        if (!limiter.tryAcquire()) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -2;
    }
    
    private String getUserIdFromToken(ServerWebExchange exchange) {
        // 从 Token 解析用户 ID
        return "1";  // 示例
    }
}

// 过滤器 3：根据路径限流
@Component
@Order(-1)
public class PathRateLimitFilter implements GlobalFilter, Ordered {
    
    private final Map<String, RateLimiter> pathLimiters = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 每个路径每秒最多 50 个请求
        RateLimiter limiter = pathLimiters.computeIfAbsent(path, 
            k -> RateLimiter.create(50));
        
        if (!limiter.tryAcquire()) {
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

**效果：**
```
请求进来后，依次经过 3 个过滤器：

第 1 层：IP 限流（每个 IP 每秒最多 100 个请求）
  ↓
  ✅ 通过

第 2 层：用户 ID 限流（每个用户每秒最多 10 个请求）
  ↓
  ✅ 通过

第 3 层：路径限流（每个路径每秒最多 50 个请求）
  ↓
  ✅ 通过

最终放行
```

---

## 四、两种方案对比

| 对比项 | 方案 1：组合 KeyResolver | 方案 2：多个过滤器 |
|--------|------------------------|------------------|
| **配置方式** | 配置文件 | 代码 |
| **代码量** | 20 行 | 150 行 |
| **难度** | ⭐⭐ 中等 | ⭐⭐⭐ 复杂 |
| **灵活性** | ⚠️ 有限 | ✅ 非常灵活 |
| **限流粒度** | 组合限流（IP+用户+路径）| 分层限流（IP、用户、路径分别限流）|
| **推荐** | ✅ 推荐（简单场景）| ⚠️ 复杂场景才用 |

---

## 五、实际案例：大厂怎么做多层限流？

### 案例：淘宝双 11

```
第 1 层：IP 限流（Gateway）
  - 每个 IP 每秒最多 100 个请求
  - 防止单个 IP 恶意刷接口
  ↓
  ✅ 通过

第 2 层：用户 ID 限流（Gateway）
  - 每个用户每秒最多 10 个请求
  - 防止单个用户恶意刷接口
  ↓
  ✅ 通过

第 3 层：接口路径限流（Gateway）
  - /order/create 每秒最多 1000 个请求
  - 保护下单接口
  ↓
  ✅ 通过

第 4 层：服务限流（Sentinel）
  - OrderService 每秒最多 500 个请求
  - 保护服务不被压垮
  ↓
  ✅ 通过

最终处理请求
```

**实现方式：**
```
第 1-3 层：Gateway 多个过滤器
第 4 层：Sentinel
```

---

## 六、完整代码示例

### 方案 1：组合 KeyResolver（推荐）

```java
// KeyResolverConfig.java
package cn.itcast.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {
    
    /**
     * 组合限流：IP + 用户 ID + 路径
     * 
     * 限流效果：
     * - 同一个 IP 的不同用户，分别限流
     * - 同一个用户的不同路径，分别限流
     * - 同一个路径的不同 IP，分别限流
     */
    @Bean
    public KeyResolver combinedKeyResolver() {
        return exchange -> {
            // 1. 获取 IP
            String ip = getIp(exchange);
            
            // 2. 获取用户 ID
            String userId = getUserId(exchange);
            
            // 3. 获取路径
            String path = getPath(exchange);
            
            // 4. 组合成一个 key
            String key = ip + ":" + userId + ":" + path;
            
            return Mono.just(key);
        };
    }
    
    /**
     * 获取客户端 IP
     */
    private String getIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress()
            .getAddress().getHostAddress();
    }
    
    /**
     * 获取用户 ID（从 Token）
     */
    private String getUserId(ServerWebExchange exchange) {
        try {
            // 从请求头获取 Token
            String token = exchange.getRequest().getHeaders()
                .getFirst("Authorization");
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                
                // 解析 Token
                Claims claims = Jwts.parser()
                    .setSigningKey("your-secret-key")
                    .parseClaimsJws(token)
                    .getBody();
                
                // 获取用户 ID
                return claims.getSubject();
            }
        } catch (Exception e) {
            // Token 解析失败
        }
        
        return "anonymous";
    }
    
    /**
     * 获取请求路径
     */
    private String getPath(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().value();
    }
}
```

```yaml
# application.yml
spring:
  redis:
    host: localhost
    port: 6379
    
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10    # 每秒 10 个请求
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@combinedKeyResolver}"  # 使用组合 KeyResolver
```

---

### 方案 2：多个过滤器（最灵活）

```java
// IpRateLimitFilter.java
package cn.itcast.gateway;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第 1 层限流：根据 IP 限流
 * 每个 IP 每秒最多 100 个请求
 */
@Component
public class IpRateLimitFilter implements GlobalFilter, Ordered {
    
    // 存储每个 IP 的限流器
    private final Map<String, RateLimiter> ipLimiters = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取客户端 IP
        String ip = exchange.getRequest().getRemoteAddress()
            .getAddress().getHostAddress();
        
        // 2. 获取或创建限流器（每个 IP 每秒最多 100 个请求）
        RateLimiter limiter = ipLimiters.computeIfAbsent(ip, 
            k -> RateLimiter.create(100));
        
        // 3. 尝试获取令牌
        if (!limiter.tryAcquire()) {
            // 没有令牌，拒绝请求
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-RateLimit-Reason", "IP limit exceeded");
            return exchange.getResponse().setComplete();
        }
        
        // 4. 有令牌，放行
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -3;  // 优先级最高（最先执行）
    }
}
```

```java
// UserRateLimitFilter.java
package cn.itcast.gateway;

import com.google.common.util.concurrent.RateLimiter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第 2 层限流：根据用户 ID 限流
 * 每个用户每秒最多 10 个请求
 */
@Component
public class UserRateLimitFilter implements GlobalFilter, Ordered {
    
    // 存储每个用户的限流器
    private final Map<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取用户 ID
        String userId = getUserIdFromToken(exchange);
        
        // 2. 获取或创建限流器（每个用户每秒最多 10 个请求）
        RateLimiter limiter = userLimiters.computeIfAbsent(userId, 
            k -> RateLimiter.create(10));
        
        // 3. 尝试获取令牌
        if (!limiter.tryAcquire()) {
            // 没有令牌，拒绝请求
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-RateLimit-Reason", "User limit exceeded");
            return exchange.getResponse().setComplete();
        }
        
        // 4. 有令牌，放行
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -2;  // 优先级第二
    }
    
    /**
     * 从 Token 获取用户 ID
     */
    private String getUserIdFromToken(ServerWebExchange exchange) {
        try {
            String token = exchange.getRequest().getHeaders()
                .getFirst("Authorization");
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                Claims claims = Jwts.parser()
                    .setSigningKey("your-secret-key")
                    .parseClaimsJws(token)
                    .getBody();
                return claims.getSubject();
            }
        } catch (Exception e) {
            // Token 解析失败
        }
        return "anonymous";
    }
}
```

```java
// PathRateLimitFilter.java
package cn.itcast.gateway;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第 3 层限流：根据路径限流
 * 每个路径每秒最多 50 个请求
 */
@Component
public class PathRateLimitFilter implements GlobalFilter, Ordered {
    
    // 存储每个路径的限流器
    private final Map<String, RateLimiter> pathLimiters = new ConcurrentHashMap<>();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取请求路径
        String path = exchange.getRequest().getPath().value();
        
        // 2. 获取或创建限流器（每个路径每秒最多 50 个请求）
        RateLimiter limiter = pathLimiters.computeIfAbsent(path, 
            k -> RateLimiter.create(50));
        
        // 3. 尝试获取令牌
        if (!limiter.tryAcquire()) {
            // 没有令牌，拒绝请求
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-RateLimit-Reason", "Path limit exceeded");
            return exchange.getResponse().setComplete();
        }
        
        // 4. 有令牌，放行
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return -1;  // 优先级第三
    }
}
```

---

## 七、运行流程图

### 方案 1：组合 KeyResolver

```
┌─────────────────────────────────────────────────────────────┐
│              组合 KeyResolver 限流流程                         │
└─────────────────────────────────────────────────────────────┘

客户端发送请求
  ↓
  http://localhost:10010/user/1
  IP: 192.168.1.100
  Token: Bearer xxx (userId=1)

Gateway 收到请求
  ↓
  执行 RequestRateLimiter 过滤器

调用 combinedKeyResolver
  ↓
  1. 获取 IP: 192.168.1.100
  2. 获取用户 ID: 1
  3. 获取路径: /user/1
  4. 组合 key: "192.168.1.100:1:/user/1"

从 Redis 检查限流
  ↓
  key = "request_rate_limiter.{192.168.1.100:1:/user/1}.tokens"
  
  检查是否有令牌：
    if (tokens > 0) {
        ✅ 有令牌，放行
    } else {
        ❌ 没有令牌，拒绝（429 Too Many Requests）
    }

如果放行
  ↓
  转发到 UserService
  http://localhost:8081/user/1

────────────────────────────────────────────────────────────

限流效果：
  - 同一个 IP + 同一个用户 + 同一个路径：每秒最多 10 个请求
  - 同一个 IP + 同一个用户 + 不同路径：分别限流
  - 同一个 IP + 不同用户：分别限流
  - 不同 IP：分别限流
```

---

### 方案 2：多个过滤器

```
┌─────────────────────────────────────────────────────────────┐
│                多个过滤器限流流程                              │
└─────────────────────────────────────────────────────────────┘

客户端发送请求
  ↓
  http://localhost:10010/user/1
  IP: 192.168.1.100
  Token: Bearer xxx (userId=1)

Gateway 收到请求
  ↓
  依次执行 3 个过滤器

┌─────────────────────────────────────────────────────────────┐
│ 第 1 层：IpRateLimitFilter（Order = -3）                     │
└─────────────────────────────────────────────────────────────┘
  
  检查 IP 限流
    ↓
    IP: 192.168.1.100
    限流：每个 IP 每秒最多 100 个请求
    
    if (IP 令牌 > 0) {
        ✅ 通过，继续下一个过滤器
    } else {
        ❌ 拒绝（429 Too Many Requests）
        响应头：X-RateLimit-Reason: IP limit exceeded
    }

┌─────────────────────────────────────────────────────────────┐
│ 第 2 层：UserRateLimitFilter（Order = -2）                   │
└─────────────────────────────────────────────────────────────┘
  
  检查用户 ID 限流
    ↓
    用户 ID: 1
    限流：每个用户每秒最多 10 个请求
    
    if (用户令牌 > 0) {
        ✅ 通过，继续下一个过滤器
    } else {
        ❌ 拒绝（429 Too Many Requests）
        响应头：X-RateLimit-Reason: User limit exceeded
    }

┌─────────────────────────────────────────────────────────────┐
│ 第 3 层：PathRateLimitFilter（Order = -1）                   │
└─────────────────────────────────────────────────────────────┘
  
  检查路径限流
    ↓
    路径: /user/1
    限流：每个路径每秒最多 50 个请求
    
    if (路径令牌 > 0) {
        ✅ 通过，转发请求
    } else {
        ❌ 拒绝（429 Too Many Requests）
        响应头：X-RateLimit-Reason: Path limit exceeded
    }

全部通过
  ↓
  转发到 UserService
  http://localhost:8081/user/1

────────────────────────────────────────────────────────────

限流效果：
  - 第 1 层：每个 IP 每秒最多 100 个请求
  - 第 2 层：每个用户每秒最多 10 个请求
  - 第 3 层：每个路径每秒最多 50 个请求
  
  只有全部通过，才能转发请求！
```

---

## 八、推荐方案

### 小白推荐：组合 KeyResolver ✅

**为什么推荐：**
- ✅ 简单，代码量少（20 行）
- ✅ 够用了（90% 的场景）
- ✅ 配置文件配置，修改方便

**适用场景：**
- 需要根据多个条件限流
- 不需要分层限流
- 简单场景

---

### 高级用户推荐：多个过滤器

**为什么推荐：**
- ✅ 灵活，功能强大
- ✅ 分层限流，更精细
- ✅ 可以针对不同层设置不同限流规则

**适用场景：**
- 需要分层限流（IP、用户、路径分别限流）
- 需要针对不同层设置不同限流规则
- 复杂场景（大厂级别）

---

## 九、总结

### 能一起用吗？

```
配置文件方式：
  ❌ 不能直接一起用（只能选一个 KeyResolver）
  ✅ 但可以用组合 KeyResolver（把多个条件组合成一个 key）

代码方式：
  ✅ 可以一起用（写多个过滤器）
```

---

### 两种方案对比

```
方案 1：组合 KeyResolver（推荐）✅
  - 配置文件配置
  - 代码量少（20 行）
  - 简单，够用
  - 组合限流（IP+用户+路径）

方案 2：多个过滤器
  - 代码配置
  - 代码量多（150 行）
  - 灵活，功能强大
  - 分层限流（IP、用户、路径分别限流）
```

---

### 一句话总结

**配置文件方式不能直接一起用多个 KeyResolver，但可以用组合 KeyResolver（把 IP、用户 ID、路径组合成一个 key）。代码方式可以一起用（写多个过滤器，每个过滤器负责一种限流）。推荐用组合 KeyResolver（简单、够用），复杂场景才用多个过滤器！**

