# Sentinel 和 Gateway 限流的区别

## 一、人话版（一句话）

**Sentinel 和 Gateway 限流就像小区的两道门：**
- Gateway 限流 = 小区大门的保安（第一道防线）
- Sentinel 限流 = 每栋楼的保安（第二道防线）
- 两道门一起用，更安全

---

## 二、核心区别

### Gateway 限流（外层防护）

```
位置：在 Gateway（网关）
作用：保护整个系统
特点：统一入口，拦截外部请求

就像：小区大门的保安
  - 所有人进小区都要过大门
  - 大门保安检查：每小时只能进 1000 人
  - 超过 1000 人，不让进
```

---

### Sentinel 限流（内层防护）

```
位置：在 UserService（服务内部）
作用：保护单个服务
特点：服务自己保护自己

就像：每栋楼的保安
  - 进了小区后，每栋楼还有保安
  - 楼栋保安检查：每小时只能进 100 人
  - 超过 100 人，不让进
```

---

## 三、为什么要两个都用？

### 场景 1：只用 Gateway 限流（不够安全）

```
┌─────────────────────────────────────────────────────────────────┐
│  外部请求（恶意攻击）                                            │
│                                                                 │
│  每秒 10000 个请求                                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 限流（第一道防线）                                      │
│                                                                 │
│  限流：每秒 1000 个请求                                          │
│  拦截：9000 个请求 ✅                                            │
│  通过：1000 个请求                                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 1000 个请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService（没有 Sentinel 限流）                               │
│                                                                 │
│  收到 1000 个请求                                                │
│                                                                 │
│  问题：                                                          │
│    - 如果 UserService 只能处理 100 个请求/秒                     │
│    - 1000 个请求会把 UserService 打垮 ❌                         │
│    - UserService 崩溃                                            │
└─────────────────────────────────────────────────────────────────┘
```

**问题：**
- Gateway 限流了，但 UserService 还是崩溃了
- 因为 Gateway 不知道 UserService 的处理能力

---

### 场景 2：只用 Sentinel 限流（不够安全）

```
┌─────────────────────────────────────────────────────────────────┐
│  外部请求（恶意攻击）                                            │
│                                                                 │
│  每秒 10000 个请求                                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway（没有限流）                                             │
│                                                                 │
│  直接转发 10000 个请求                                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 10000 个请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService（有 Sentinel 限流）                                 │
│                                                                 │
│  Sentinel 限流：每秒 100 个请求                                  │
│  拦截：9900 个请求 ✅                                            │
│  通过：100 个请求                                                │
│                                                                 │
│  问题：                                                          │
│    - 10000 个请求都到达了 UserService                            │
│    - UserService 要处理 10000 次限流检查                         │
│    - 浪费 UserService 的资源 ❌                                  │
└─────────────────────────────────────────────────────────────────┘
```

**问题：**
- 虽然 Sentinel 限流了，但 UserService 还是要处理 10000 次限流检查
- 浪费资源

---

### 场景 3：Gateway + Sentinel 双重限流（推荐）✅

```
┌─────────────────────────────────────────────────────────────────┐
│  外部请求（恶意攻击）                                            │
│                                                                 │
│  每秒 10000 个请求                                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 限流（第一道防线）                                      │
│                                                                 │
│  限流：每秒 1000 个请求                                          │
│  拦截：9000 个请求 ✅                                            │
│  通过：1000 个请求                                               │
│                                                                 │
│  作用：拦截大部分恶意请求                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 1000 个请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService + Sentinel 限流（第二道防线）                       │
│                                                                 │
│  Sentinel 限流：每秒 100 个请求                                  │
│  拦截：900 个请求 ✅                                             │
│  通过：100 个请求                                                │
│                                                                 │
│  作用：保护 UserService 不被打垮                                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 100 个请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService 处理请求                                            │
│                                                                 │
│  只处理 100 个请求                                               │
│  UserService 稳定运行 ✅                                         │
└─────────────────────────────────────────────────────────────────┘
```

**优点：**
- ✅ Gateway 拦截 90% 的请求（9000 个）
- ✅ Sentinel 拦截剩余 90% 的请求（900 个）
- ✅ UserService 只处理 100 个请求
- ✅ 总拦截率：99%（10000 → 100）
- ✅ UserService 稳定运行

---

## 四、详细对比

### Gateway 限流

**位置：**
- 在 Gateway（网关）

**作用：**
- 保护整个系统
- 拦截外部恶意请求

**限流粒度：**
- 全局限流（所有请求）
- 接口限流（/user/**, /order/**）
- IP 限流（每个 IP）

**优点：**
- ✅ 统一入口，方便管理
- ✅ 拦截大部分恶意请求
- ✅ 减轻后端服务压力

**缺点：**
- ❌ 不知道每个服务的处理能力
- ❌ 可能设置不准确

**代码示例：**

```java
@Component
public class GatewayRateLimitFilter implements GlobalFilter {
    // Gateway 限流：每秒 1000 个请求
    private final RateLimiter rateLimiter = RateLimiter.create(1000);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimiter.tryAcquire()) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
```

---

### Sentinel 限流

**位置：**
- 在 UserService（服务内部）

**作用：**
- 保护单个服务
- 服务自己保护自己

**限流粒度：**
- 方法级别（queryById、login）
- 资源级别（数据库连接、线程池）
- 更精细

**优点：**
- ✅ 服务自己知道自己的处理能力
- ✅ 限流更精确
- ✅ 支持熔断、降级等高级功能

**缺点：**
- ❌ 每个服务都要配置
- ❌ 不能拦截外部恶意请求

**代码示例：**

```java
@Service
public class UserService {
    
    // Sentinel 限流：每秒 100 个请求
    @SentinelResource(value = "queryById", 
                      blockHandler = "handleBlock")
    public User queryById(Long id) {
        // 查数据库
        return userMapper.selectById(id);
    }
    
    // 限流后的处理
    public User handleBlock(Long id, BlockException ex) {
        throw new RuntimeException("请求太频繁，请稍后重试");
    }
}
```

---

## 五、对比表

| 对比项 | Gateway 限流 | Sentinel 限流 |
|--------|-------------|--------------|
| 位置 | Gateway（网关） | UserService（服务内部） |
| 作用 | 保护整个系统 | 保护单个服务 |
| 限流粒度 | 接口级别 | 方法级别 |
| 拦截时机 | 请求到达 Gateway | 请求到达 UserService |
| 优点 | 统一入口，拦截恶意请求 | 限流精确，支持熔断降级 |
| 缺点 | 不知道服务处理能力 | 不能拦截外部恶意请求 |
| 推荐 | ✅ 必须用 | ✅ 必须用 |

---

## 六、推荐方案（双重限流）

### 方案：Gateway + Sentinel 双重限流

```
第 1 层：Gateway 限流（粗粒度）
  - 限流：每秒 1000 个请求
  - 作用：拦截大部分恶意请求
  - 拦截率：90%

第 2 层：Sentinel 限流（细粒度）
  - 限流：每秒 100 个请求
  - 作用：保护 UserService 不被打垮
  - 拦截率：90%

总拦截率：99%（10000 → 100）
```

---

### 配置建议

#### Gateway 限流配置

```java
@Component
public class GatewayRateLimitFilter implements GlobalFilter {
    
    private Map<String, RateLimiter> rateLimiters = new HashMap<>();
    
    public GatewayRateLimitFilter() {
        // 不同服务不同限流（根据服务处理能力的 10 倍）
        rateLimiters.put("/user/**", RateLimiter.create(1000));    // UserService 能处理 100/秒，Gateway 限制 1000/秒
        rateLimiters.put("/order/**", RateLimiter.create(500));    // OrderService 能处理 50/秒，Gateway 限制 500/秒
        rateLimiters.put("/product/**", RateLimiter.create(2000)); // ProductService 能处理 200/秒，Gateway 限制 2000/秒
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        RateLimiter rateLimiter = rateLimiters.entrySet().stream()
            .filter(entry -> path.startsWith(entry.getKey().replace("/**", "")))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
        
        return chain.filter(exchange);
    }
}
```

---

#### Sentinel 限流配置

```java
@Service
public class UserService {
    
    // 查询用户：每秒 100 个请求
    @SentinelResource(value = "queryById", 
                      blockHandler = "handleBlock")
    public User queryById(Long id) {
        return userMapper.selectById(id);
    }
    
    // 登录：每秒 10 个请求（登录慢）
    @SentinelResource(value = "login", 
                      blockHandler = "handleBlock")
    public User login(String username, String password) {
        return userMapper.selectByUsernameAndPassword(username, password);
    }
    
    // 限流后的处理
    public User handleBlock(Long id, BlockException ex) {
        throw new RuntimeException("请求太频繁，请稍后重试");
    }
}

// Sentinel 配置
@Configuration
public class SentinelConfig {
    
    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        // queryById：每秒 100 个请求
        FlowRule rule1 = new FlowRule();
        rule1.setResource("queryById");
        rule1.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule1.setCount(100);
        rules.add(rule1);
        
        // login：每秒 10 个请求
        FlowRule rule2 = new FlowRule();
        rule2.setResource("login");
        rule2.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule2.setCount(10);
        rules.add(rule2);
        
        FlowRuleManager.loadRules(rules);
    }
}
```

---

## 七、实际案例

### 案例：电商系统

**系统架构：**
- Gateway：1 台
- UserService：10 台
- OrderService：10 台
- ProductService：10 台

**限流配置：**

```
Gateway 限流（第一道防线）：
  - /user/**：每秒 10000 个请求（10 台 × 1000）
  - /order/**：每秒 5000 个请求（10 台 × 500）
  - /product/**：每秒 20000 个请求（10 台 × 2000）

Sentinel 限流（第二道防线）：
  - UserService：每台每秒 100 个请求
  - OrderService：每台每秒 50 个请求
  - ProductService：每台每秒 200 个请求
```

**效果：**

```
外部请求：每秒 100000 个请求（恶意攻击）
  ↓
Gateway 限流：拦截 65000 个请求
  ↓ 35000 个请求
Sentinel 限流：拦截 34000 个请求
  ↓ 1000 个请求
服务处理：1000 个请求

总拦截率：99%（100000 → 1000）
服务稳定运行 ✅
```

---

## 八、总结

### 为什么要两个都用？

```
1. Gateway 限流（第一道防线）
   - 拦截大部分恶意请求
   - 减轻后端服务压力
   - 拦截率：90%

2. Sentinel 限流（第二道防线）
   - 保护服务不被打垮
   - 限流更精确
   - 拦截率：90%

3. 双重限流（推荐）
   - 总拦截率：99%
   - 服务稳定运行
```

### 配置建议

```
Gateway 限流：
  - 设置为服务处理能力的 10 倍
  - 例如：UserService 能处理 100/秒，Gateway 限制 1000/秒

Sentinel 限流：
  - 设置为服务实际处理能力
  - 例如：UserService 能处理 100/秒，Sentinel 限制 100/秒
```

### 一句话总结

**Sentinel 和 Gateway 限流就像小区的两道门：Gateway 是小区大门的保安（第一道防线，拦截 90% 的恶意请求），Sentinel 是每栋楼的保安（第二道防线，保护服务不被打垮）。两道门一起用，总拦截率 99%，服务更安全！Gateway 限流设置为服务处理能力的 10 倍，Sentinel 限流设置为服务实际处理能力。**
