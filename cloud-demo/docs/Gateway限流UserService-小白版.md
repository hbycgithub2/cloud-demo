# Gateway 限流 UserService - 小白版

## 一、人话版（一句话）

**Gateway 限流就像餐厅门口的保安：**
- UserService 是餐厅（只有 10 张桌子）
- Gateway 是保安（控制进入人数）
- 每秒只让 10 个人进去（限流规则）
- 第 11 个人来了，保安说：等一会儿再来（拒绝请求）

---

## 二、为什么要限流 UserService？

### 场景：没有限流（UserService 崩溃）

```
正常情况：
  每秒 10 个请求 → UserService 处理 ✅

突然流量暴增：
  每秒 1000 个请求 → UserService 处理不过来 ❌
  
结果：
  - UserService CPU 100%
  - 数据库连接池满了
  - UserService 崩溃
  - 所有用户都无法查询用户信息
```

**人话：**
- 餐厅只有 10 张桌子
- 突然来了 100 个客人
- 餐厅挤爆了，谁都吃不了饭
- 餐厅崩溃

---

### 场景：有限流（保护 UserService）

```
正常情况：
  每秒 10 个请求 → UserService 处理 ✅

突然流量暴增：
  每秒 1000 个请求
  → Gateway 限流：只让 100 个请求通过
  → 其他 900 个请求被拒绝
  
结果：
  - UserService 处理 100 个请求 ✅
  - 900 个请求被拒绝（返回 429 错误）
  - UserService 不会崩溃
  - 至少 100 个用户可以正常查询
```

**人话：**
- 餐厅只有 10 张桌子
- 突然来了 100 个客人
- 保安说：只能进 10 个，其他人排队
- 至少 10 个客人可以吃饭
- 餐厅不会崩溃

---

## 三、完整流程图

### 场景：限流规则（每秒最多 10 个请求）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：11 个客户端同时发送请求                                │
│                                                                 │
│  客户端 1：GET /user/1                                           │
│  客户端 2：GET /user/2                                           │
│  客户端 3：GET /user/3                                           │
│  ...                                                            │
│  客户端 10：GET /user/10                                         │
│  客户端 11：GET /user/11                                         │
│                                                                 │
│  11 个请求同时到达 Gateway                                       │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 11 个请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Gateway 收到请求                                       │
│                                                                 │
│  Gateway (localhost:10010) 收到 11 个请求                        │
│                                                                 │
│  Gateway 想：这么多请求，我得限流保护 UserService！              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：限流过滤器检查                                         │
│                                                                 │
│  限流规则：                                                      │
│    路径：/user/**                                                │
│    限制：每秒最多 10 个请求                                      │
│                                                                 │
│  检查过程：                                                      │
│    当前时间：10:00:00                                            │
│    当前秒已有请求数：0                                           │
│                                                                 │
│  处理请求 1：                                                    │
│    请求数 = 0 + 1 = 1                                            │
│    1 <= 10？✅ 是的，通过                                        │
│                                                                 │
│  处理请求 2：                                                    │
│    请求数 = 1 + 1 = 2                                            │
│    2 <= 10？✅ 是的，通过                                        │
│                                                                 │
│  ...                                                            │
│                                                                 │
│  处理请求 10：                                                   │
│    请求数 = 9 + 1 = 10                                           │
│    10 <= 10？✅ 是的，通过                                       │
│                                                                 │
│  处理请求 11：                                                   │
│    请求数 = 10 + 1 = 11                                          │
│    11 <= 10？❌ 不是，拒绝！                                     │
│                                                                 │
│  结果：                                                          │
│    前 10 个请求 → 通过 ✅                                        │
│    第 11 个请求 → 拒绝 ❌                                        │
│                                                                 │
│  Gateway 想：                                                    │
│    前 10 个请求可以转发到 UserService                            │
│    第 11 个请求直接拒绝，不转发                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ├─ 前 10 个请求（通过）
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：转发到 UserService（前 10 个请求）                     │
│                                                                 │
│  Gateway → UserService：                                         │
│    请求 1：GET http://localhost:8081/user/1                     │
│    请求 2：GET http://localhost:8081/user/2                     │
│    ...                                                          │
│    请求 10：GET http://localhost:8081/user/10                   │
│                                                                 │
│  只有 10 个请求到达 UserService                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：UserService 处理请求                                   │
│                                                                 │
│  UserService (localhost:8081) 收到 10 个请求                     │
│                                                                 │
│  UserService 想：                                                │
│    只有 10 个请求，我能处理 ✅                                   │
│                                                                 │
│  处理过程：                                                      │
│    请求 1：查数据库 → 返回用户 1 的信息                          │
│    请求 2：查数据库 → 返回用户 2 的信息                          │
│    ...                                                          │
│    请求 10：查数据库 → 返回用户 10 的信息                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回数据
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：Gateway 转发响应（前 10 个请求）                       │
│                                                                 │
│  Gateway → 客户端 1-10：                                         │
│    客户端 1：{"id":1,"username":"柳岩","address":"湖南"}         │
│    客户端 2：{"id":2,"username":"张三","address":"北京"}         │
│    ...                                                          │
│    客户端 10：{"id":10,"username":"王五","address":"上海"}       │
│                                                                 │
│  前 10 个客户端收到数据 ✅                                       │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 第 11 个请求（被拒绝）
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 7 步：返回限流错误（第 11 个请求）                           │
│                                                                 │
│  Gateway → 客户端 11：                                           │
│    状态码：429 Too Many Requests                                │
│    消息：{"code":429,"message":"请求太频繁，请稍后重试"}        │
│                                                                 │
│  客户端 11 想：                                                  │
│    被限流了，等一会儿再试                                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、简化版流程图

```
11 个客户端
  │
  │ ① 发送 11 个请求：GET /user/1, /user/2, ..., /user/11
  ↓
Gateway (10010)
  │
  │ ② 收到 11 个请求
  │ ③ 限流过滤器检查
  │    规则：/user/** 每秒最多 10 个请求
  │    当前秒已有：0 个
  │    
  │    请求 1-10：0+10 = 10 <= 10 ✅ 通过
  │    请求 11：10+1 = 11 > 10 ❌ 拒绝
  │
  ├─ 请求 1-10（通过）
  │   │
  │   │ ④ 转发到 UserService
  │   ↓
  │  UserService (8081)
  │   │
  │   │ ⑤ 处理 10 个请求
  │   │ ⑥ 返回数据
  │   ↓
  │  Gateway
  │   │
  │   │ ⑦ 转发响应
  │   ↓
  │  客户端 1-10
  │   │
  │   │ ⑧ 收到数据 ✅
  │
  └─ 请求 11（拒绝）
      │
      │ ④ 返回 429 错误
      ↓
     客户端 11
      │
      │ ⑤ 收到错误：请求太频繁 ❌
```

---

## 五、核心代码（超级简单）

### 方案 1：全局限流（最简单）

```java
// 1. 添加依赖
// pom.xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>31.1-jre</version>
</dependency>

// 2. 写一个过滤器
@Component
public class UserServiceRateLimitFilter implements GlobalFilter, Ordered {
    
    // 限流：每秒最多 10 个请求
    private final RateLimiter rateLimiter = RateLimiter.create(10);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 只限流 /user/** 路径
        if (path.startsWith("/user/")) {
            // 尝试获取 1 个令牌
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

**就这么简单！**

---

### 方案 2：针对不同接口不同限流（推荐）

```java
@Component
public class UserServiceRateLimitFilter implements GlobalFilter, Ordered {
    
    // 不同接口不同限流
    private Map<String, RateLimiter> rateLimiters = new HashMap<>();
    
    public UserServiceRateLimitFilter() {
        // /user/list 查询列表：每秒 100 个请求（查询快）
        rateLimiters.put("/user/list", RateLimiter.create(100));
        
        // /user/{id} 查询单个：每秒 50 个请求
        rateLimiters.put("/user/", RateLimiter.create(50));
        
        // /user/login 登录：每秒 10 个请求（登录慢）
        rateLimiters.put("/user/login", RateLimiter.create(10));
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 找到对应的限流器
        RateLimiter rateLimiter = rateLimiters.entrySet().stream()
            .filter(entry -> path.startsWith(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        
        // 如果有限流器，检查限流
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
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

---

## 六、配置说明

### 限流规则怎么设置？

```
根据 UserService 的处理能力：

1. 压测 UserService
   - 用 JMeter 或 Postman 压测
   - 看 UserService 每秒能处理多少请求
   - 假设能处理 100 个请求/秒

2. 设置限流规则
   - 限流设置为 80 个请求/秒（留 20% 余量）
   - 代码：RateLimiter.create(80)

3. 观察效果
   - 如果 UserService 还是很慢，降低限流（60 个/秒）
   - 如果 UserService 很轻松，提高限流（100 个/秒）
```

---

### 不同接口不同限流

```
查询接口（快）：
  - /user/list → 100 个/秒
  - /user/{id} → 50 个/秒

写入接口（慢）：
  - /user/login → 10 个/秒
  - /user/register → 10 个/秒
  - /user/update → 5 个/秒

原因：
  - 查询只读数据库，快
  - 写入要写数据库，慢
```

---

## 七、实际测试

### 测试 1：正常请求（通过）

```bash
# 发送 10 个请求（每秒）
for i in {1..10}; do
  curl http://localhost:10010/user/$i
done

# 结果：
{"id":1,"username":"柳岩","address":"湖南"}
{"id":2,"username":"张三","address":"北京"}
...
{"id":10,"username":"王五","address":"上海"}

# ✅ 全部通过
```

---

### 测试 2：超过限流（拒绝）

```bash
# 发送 20 个请求（每秒）
for i in {1..20}; do
  curl http://localhost:10010/user/$i
done

# 结果：
{"id":1,"username":"柳岩","address":"湖南"}  ← 请求 1-10 通过
{"id":2,"username":"张三","address":"北京"}
...
{"id":10,"username":"王五","address":"上海"}
{"code":429,"message":"请求太频繁，请稍后重试"}  ← 请求 11-20 被拒绝
{"code":429,"message":"请求太频繁，请稍后重试"}
...

# ✅ 前 10 个通过，后 10 个被拒绝
```

---

### 测试 3：等一秒后再试（通过）

```bash
# 发送 10 个请求
for i in {1..10}; do
  curl http://localhost:10010/user/$i
done

# 等 1 秒
sleep 1

# 再发送 10 个请求
for i in {11..20}; do
  curl http://localhost:10010/user/$i
done

# 结果：
# 第 1 批（请求 1-10）：全部通过 ✅
# 第 2 批（请求 11-20）：全部通过 ✅

# 原因：等了 1 秒，限流计数器重置了
```

---

## 八、常见问题

### Q1：为什么要限流 UserService？

**A：保护 UserService 不崩溃**

```
没有限流：
  - 流量暴增 → UserService 崩溃 → 所有用户都无法访问

有限流：
  - 流量暴增 → Gateway 限流 → 部分用户可以访问
  - 至少保证 UserService 不崩溃
```

---

### Q2：限流规则设置多少合适？

**A：根据 UserService 的处理能力**

```
步骤：
  1. 压测 UserService（用 JMeter）
  2. 看 UserService 每秒能处理多少请求
  3. 限流设置为处理能力的 80%（留 20% 余量）

示例：
  - UserService 能处理 100 个/秒
  - 限流设置为 80 个/秒
  - 代码：RateLimiter.create(80)
```

---

### Q3：限流后用户体验不好怎么办？

**A：返回友好的提示**

```java
if (!rateLimiter.tryAcquire()) {
    // 返回友好的提示
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    
    String message = "{\"code\":429,\"message\":\"请求太频繁，请稍后重试\"}";
    DataBuffer buffer = response.bufferFactory().wrap(message.getBytes());
    
    return response.writeWith(Mono.just(buffer));
}
```

---

### Q4：限流会不会影响性能？

**A：几乎不影响**

```
限流耗时：< 0.1 毫秒
查数据库耗时：10 毫秒

对比：
  - 限流：0.1 毫秒
  - 查数据库：10 毫秒
  - 限流只占 1%

结论：不用担心性能
```

---

## 九、总结

### 核心流程

```
1. 客户端发送 11 个请求
2. Gateway 收到 11 个请求
3. 限流过滤器检查
   - 前 10 个请求：通过 ✅
   - 第 11 个请求：拒绝 ❌
4. 前 10 个请求转发到 UserService
5. UserService 处理 10 个请求
6. 返回数据给前 10 个客户端
7. 第 11 个客户端收到 429 错误
```

### 核心代码（只需要 3 行）

```java
@Component
public class UserServiceRateLimitFilter implements GlobalFilter {
    // 限流：每秒最多 10 个请求
    private final RateLimiter rateLimiter = RateLimiter.create(10);  // ← 第 1 行
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getPath().value().startsWith("/user/")) {
            if (!rateLimiter.tryAcquire()) {  // ← 第 2 行
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);  // ← 第 3 行
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }
}
```

### 一句话总结

**Gateway 限流 UserService 就像餐厅门口的保安：UserService 只有 10 张桌子（每秒处理 10 个请求），Gateway 是保安（限流过滤器），每秒只让 10 个人进去，第 11 个人来了保安说"等一会儿再来"（返回 429 错误），保护餐厅不会挤爆（保护 UserService 不崩溃）。代码超级简单，只需要 3 行！**
