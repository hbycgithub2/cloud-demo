# Gateway 鉴权过滤器的作用

## 一、为什么要加 authorization=admin？

### 1.1 这是一个教学示例

```java
@Component
public class AuthorizeFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求参数中的 authorization
        String auth = params.getFirst("authorization");
        
        // 如果 authorization=admin，放行
        if ("admin".equals(auth)) {
            return chain.filter(exchange);
        }
        
        // 否则，返回 401 Unauthorized
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
```

**这是一个简化的鉴权示例，用来演示 Gateway 的全局过滤器功能。**

## 二、真实场景中的鉴权

### 2.1 没有 Gateway 的情况

```
┌─────────────────────────────────────────────────────────────────┐
│  问题：每个服务都要自己做鉴权                                     │
│                                                                 │
│  客户端 → UserService                                            │
│    UserService 检查：有没有登录？有没有权限？                     │
│                                                                 │
│  客户端 → OrderService                                           │
│    OrderService 检查：有没有登录？有没有权限？                    │
│                                                                 │
│  客户端 → ProductService                                         │
│    ProductService 检查：有没有登录？有没有权限？                  │
│                                                                 │
│  缺点：                                                          │
│    1. 每个服务都要写鉴权代码（重复）                              │
│    2. 鉴权逻辑不统一，容易出错                                   │
│    3. 修改鉴权规则，所有服务都要改                                │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 有 Gateway 的情况

```
┌─────────────────────────────────────────────────────────────────┐
│  优势：在 Gateway 统一鉴权                                        │
│                                                                 │
│  客户端 → Gateway                                                │
│    ↓                                                            │
│  Gateway 检查：有没有登录？有没有权限？                           │
│    ├─ 没有 → 直接返回 401，不转发                                │
│    └─ 有 → 转发到后端服务                                        │
│         ↓                                                       │
│       UserService/OrderService/ProductService                   │
│       (不需要再检查权限，Gateway 已经检查过了)                    │
│                                                                 │
│  优点：                                                          │
│    1. 鉴权代码只写一次（在 Gateway）                             │
│    2. 鉴权逻辑统一，不会出错                                     │
│    3. 修改鉴权规则，只改 Gateway                                 │
│    4. 后端服务更简单，专注业务逻辑                                │
└─────────────────────────────────────────────────────────────────┘
```

## 三、真实项目中的鉴权流程

### 3.1 基于 Token 的鉴权（常见方式）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：用户登录                                                │
│                                                                 │
│  客户端 → Gateway → UserService                                  │
│    POST /user/login                                             │
│    {"username":"admin","password":"123456"}                     │
│                                                                 │
│  UserService 验证成功后：                                        │
│    生成 Token：eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...          │
│    返回给客户端                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：客户端保存 Token                                        │
│                                                                 │
│  客户端（浏览器/APP）：                                           │
│    保存 Token 到：                                               │
│      - LocalStorage（浏览器）                                    │
│      - Cookie                                                   │
│      - 内存（APP）                                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：后续请求带上 Token                                      │
│                                                                 │
│  客户端 → Gateway                                                │
│    GET /user/info                                               │
│    Headers:                                                     │
│      Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：Gateway 验证 Token                                      │
│                                                                 │
│  Gateway 的鉴权过滤器：                                           │
│    1. 从请求头获取 Token                                         │
│    2. 验证 Token 是否有效                                        │
│       - 是否过期？                                               │
│       - 签名是否正确？                                           │
│       - 是否被篡改？                                             │
│    3. 解析 Token 获取用户信息                                    │
│       - userId: 1                                               │
│       - username: admin                                         │
│       - roles: [ADMIN, USER]                                    │
│    4. 检查权限                                                   │
│       - 这个用户能访问这个接口吗？                                │
│                                                                 │
│  验证通过 → 转发到后端服务                                        │
│  验证失败 → 返回 401 Unauthorized                                │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 真实的鉴权过滤器代码

```java
@Component
public class AuthorizeFilter implements GlobalFilter {
    
    @Autowired
    private JwtUtil jwtUtil;  // JWT 工具类
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取请求头中的 Token
        String token = exchange.getRequest()
            .getHeaders()
            .getFirst("Authorization");
        
        // 2. 检查 Token 是否存在
        if (token == null || !token.startsWith("Bearer ")) {
            // 没有 Token，返回 401
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        // 3. 去掉 "Bearer " 前缀
        token = token.substring(7);
        
        try {
            // 4. 验证 Token
            Claims claims = jwtUtil.parseToken(token);
            
            // 5. 获取用户信息
            String userId = claims.get("userId", String.class);
            String username = claims.get("username", String.class);
            
            // 6. 将用户信息传递给后端服务（通过请求头）
            ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-Username", username)
                .build();
            
            // 7. 放行
            return chain.filter(exchange.mutate().request(request).build());
            
        } catch (Exception e) {
            // Token 无效或过期
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
```

## 四、为什么示例代码用 authorization=admin？

### 4.1 简化教学

```
真实鉴权（复杂）：
  1. 需要 JWT 库
  2. 需要加密解密
  3. 需要数据库查询
  4. 需要 Redis 缓存
  5. 代码很长

示例鉴权（简单）：
  1. 只检查参数 authorization=admin
  2. 不需要任何依赖
  3. 代码只有几行
  4. 容易理解 Gateway 过滤器的工作原理

目的：让你理解 Gateway 可以在转发前做检查
```

### 4.2 演示过滤器功能

```
这个简单的例子演示了：

1. 全局过滤器的概念
   - 所有请求都会经过这个过滤器

2. 请求拦截
   - 可以在转发前检查请求
   - 不符合条件的请求直接拦截

3. 条件判断
   - if ("admin".equals(auth)) → 放行
   - else → 拦截

4. 返回错误
   - setStatusCode(HttpStatus.UNAUTHORIZED) → 401
   - setComplete() → 结束请求，不转发
```

## 五、实际应用场景

### 5.1 场景 1：登录验证

```
需求：只有登录用户才能访问

Gateway 鉴权过滤器：
  1. 检查请求头中的 Token
  2. 验证 Token 是否有效
  3. 有效 → 放行
  4. 无效 → 返回 401，跳转到登录页
```

### 5.2 场景 2：权限控制

```
需求：不同角色有不同权限

Gateway 鉴权过滤器：
  1. 解析 Token 获取用户角色
  2. 检查当前接口需要什么权限
  3. 用户有权限 → 放行
  4. 用户无权限 → 返回 403 Forbidden

示例：
  - /admin/** 需要 ADMIN 角色
  - /user/** 需要 USER 角色
  - /public/** 不需要权限
```

### 5.3 场景 3：IP 白名单

```
需求：只允许特定 IP 访问

Gateway 鉴权过滤器：
  1. 获取请求的 IP 地址
  2. 检查 IP 是否在白名单中
  3. 在白名单 → 放行
  4. 不在白名单 → 返回 403
```

### 5.4 场景 4：限流

```
需求：防止恶意请求，限制访问频率

Gateway 鉴权过滤器：
  1. 记录每个 IP 的请求次数
  2. 检查是否超过限制（如 1 分钟 100 次）
  3. 未超过 → 放行
  4. 超过 → 返回 429 Too Many Requests
```

## 六、对比总结

### 6.1 示例代码 vs 真实代码

| 对比项 | 示例代码 | 真实代码 |
|-------|---------|---------|
| 验证方式 | 参数 `authorization=admin` | 请求头 `Authorization: Bearer token` |
| 复杂度 | 简单，几行代码 | 复杂，需要 JWT、加密等 |
| 安全性 | 不安全，任何人都能猜到 | 安全，Token 加密且有过期时间 |
| 目的 | 教学演示 | 生产环境使用 |

### 6.2 为什么要统一鉴权？

```
优势：
  1. 代码复用：鉴权逻辑只写一次
  2. 统一管理：所有鉴权规则在一个地方
  3. 安全性高：统一的安全策略
  4. 易于维护：修改鉴权规则只改 Gateway
  5. 性能优化：不合法的请求在 Gateway 就拦截，不浪费后端资源

劣势：
  1. Gateway 成为单点：Gateway 挂了，所有服务都不可用
  2. 增加延迟：多了一层转发
```

## 七、如何使用

### 7.1 测试时（开发环境）

```
方式 1：加参数
  http://localhost:10010/user/1?authorization=admin

方式 2：注释掉过滤器
  //@Component  // 注释掉，禁用鉴权
  public class AuthorizeFilter implements GlobalFilter {
      ...
  }

方式 3：修改过滤器逻辑
  public Mono<Void> filter(...) {
      // 直接放行，不检查
      return chain.filter(exchange);
  }
```

### 7.2 生产环境

```
1. 用户登录获取 Token
   POST /user/login
   返回：{"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}

2. 后续请求带上 Token
   GET /user/info
   Headers:
     Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

3. Gateway 验证 Token
   - 有效 → 转发到后端
   - 无效 → 返回 401
```

## 八、总结

### 8.1 一句话总结

> **`authorization=admin` 是一个简化的教学示例，用来演示 Gateway 可以在转发请求前做统一鉴权。真实项目中会用 JWT Token 进行鉴权，更安全也更复杂。**

### 8.2 核心要点

1. **统一鉴权**：在 Gateway 统一检查权限，后端服务不需要重复检查
2. **全局过滤器**：所有请求都会经过这个过滤器
3. **请求拦截**：不符合条件的请求直接拦截，不转发到后端
4. **简化示例**：`authorization=admin` 只是教学用，真实项目用 JWT Token

### 8.3 实际建议

**开发测试时：**
- 注释掉 `@Component`，禁用鉴权过滤器
- 或者修改逻辑，直接放行所有请求

**生产环境：**
- 实现基于 JWT 的鉴权
- 添加权限控制
- 添加 IP 白名单
- 添加限流保护
