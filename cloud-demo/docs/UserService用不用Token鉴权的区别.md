# UserService 用不用 Token 鉴权的区别

## 一、核心问题

**问题：UserService 服务用不用 Token 鉴权会怎么样？**

**答案：取决于你的架构！**

---

## 二、三种架构对比

### 架构 1：没有 Gateway，UserService 自己鉴权

```
客户端 → UserService
           │
           ├─ 验证 Token ✅
           ├─ 处理业务逻辑
           └─ 返回数据
```

**特点：**
- UserService 必须自己验证 Token
- 没有统一入口
- 每个服务都要写鉴权代码

---

### 架构 2：有 Gateway，Gateway 统一鉴权，UserService 不鉴权（推荐）✅

```
客户端 → Gateway → UserService
           │
           ├─ 验证 Token ✅
           └─ 转发请求
                    │
                    ├─ 不验证 Token
                    ├─ 处理业务逻辑
                    └─ 返回数据
```

**特点：**
- Gateway 统一验证 Token
- UserService 不需要验证 Token
- 代码简洁，维护方便

---

### 架构 3：有 Gateway，Gateway 和 UserService 都鉴权（双重保险）

```
客户端 → Gateway → UserService
           │           │
           ├─ 验证 Token ✅
           └─ 转发请求
                    │
                    ├─ 再次验证 Token ✅
                    ├─ 处理业务逻辑
                    └─ 返回数据
```

**特点：**
- Gateway 验证一次
- UserService 再验证一次
- 双重保险，更安全
- 但是性能差，代码重复

---

## 三、详细对比

### 场景 1：没有 Gateway，UserService 自己鉴权

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端发送请求                                                  │
│                                                                 │
│  GET http://localhost:8081/user/1                               │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGci...                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 直接访问 UserService
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService 收到请求                                            │
│                                                                 │
│  UserService 必须自己验证 Token：                                │
│                                                                 │
│  @Component                                                     │
│  public class AuthFilter {                                      │
│      public void filter(HttpServletRequest request) {          │
│          // 1. 获取 Token                                       │
│          String token = request.getHeader("Authorization");     │
│                                                                 │
│          // 2. 验证 Token                                       │
│          Claims claims = Jwts.parser()                          │
│              .setSigningKey("secret-key")                       │
│              .parseClaimsJws(token)                             │
│              .getBody();                                        │
│                                                                 │
│          // 3. Token 有效，继续处理                             │
│      }                                                          │
│  }                                                              │
│                                                                 │
│  验证通过后，处理业务逻辑                                        │
└─────────────────────────────────────────────────────────────────┘
```

**优点：**
- 简单直接

**缺点：**
- ❌ 每个服务都要写鉴权代码
- ❌ 代码重复
- ❌ 维护困难
- ❌ 没有统一入口

---

### 场景 2：有 Gateway，Gateway 统一鉴权，UserService 不鉴权（推荐）✅

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端发送请求                                                  │
│                                                                 │
│  GET http://localhost:10010/user/1                              │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGci...                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 通过 Gateway
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 验证 Token                                              │
│                                                                 │
│  @Component                                                     │
│  public class JwtAuthFilter implements GlobalFilter {           │
│      public Mono<Void> filter(...) {                            │
│          // 1. 获取 Token                                       │
│          String token = request.getHeader("Authorization");     │
│                                                                 │
│          // 2. 验证 Token                                       │
│          Claims claims = Jwts.parser()                          │
│              .setSigningKey("secret-key")                       │
│              .parseClaimsJws(token)                             │
│              .getBody();                                        │
│                                                                 │
│          // 3. 添加用户信息到请求头                             │
│          request.mutate()                                       │
│              .header("X-User-Id", userId)                       │
│              .header("X-Username", username)                    │
│              .build();                                          │
│                                                                 │
│          // 4. 转发请求                                         │
│          return chain.filter(exchange);                         │
│      }                                                          │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求（带用户信息）
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService 收到请求                                            │
│                                                                 │
│  UserService 不需要验证 Token：                                  │
│                                                                 │
│  @GetMapping("/{id}")                                           │
│  public User queryById(                                         │
│      @PathVariable Long id,                                     │
│      @RequestHeader("X-User-Id") Long userId  ← 从请求头获取    │
│  ) {                                                            │
│      // 直接处理业务逻辑，不需要验证 Token                       │
│      System.out.println("当前用户：" + userId);                 │
│      return userService.queryById(id);                          │
│  }                                                              │
│                                                                 │
│  UserService 想：                                                │
│    请求能到我这里，说明 Gateway 已经验证过了                     │
│    我不需要再验证，直接处理业务逻辑                              │
└─────────────────────────────────────────────────────────────────┘
```

**优点：**
- ✅ 鉴权代码只写一次（在 Gateway）
- ✅ UserService 代码简洁
- ✅ 维护方便
- ✅ 统一入口

**缺点：**
- ⚠️ 如果直接访问 UserService（绕过 Gateway），就没有鉴权了

---

### 场景 3：有 Gateway，Gateway 和 UserService 都鉴权（双重保险）

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端发送请求                                                  │
│                                                                 │
│  GET http://localhost:10010/user/1                              │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGci...                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 通过 Gateway
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 验证 Token（第 1 次）                                   │
│                                                                 │
│  验证 Token ✅                                                   │
│  添加用户信息到请求头                                            │
│  转发请求                                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求（带 Token 和用户信息）
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService 再次验证 Token（第 2 次）                           │
│                                                                 │
│  @Component                                                     │
│  public class AuthFilter {                                      │
│      public void filter(HttpServletRequest request) {          │
│          // 再次验证 Token                                      │
│          String token = request.getHeader("Authorization");     │
│          Claims claims = Jwts.parser()                          │
│              .setSigningKey("secret-key")                       │
│              .parseClaimsJws(token)                             │
│              .getBody();                                        │
│      }                                                          │
│  }                                                              │
│                                                                 │
│  验证通过后，处理业务逻辑                                        │
└─────────────────────────────────────────────────────────────────┘
```

**优点：**
- ✅ 双重保险，更安全
- ✅ 即使直接访问 UserService，也有鉴权

**缺点：**
- ❌ 验证两次，性能差
- ❌ 代码重复
- ❌ 维护困难

---

## 四、如果不用 Token 鉴权会怎么样？

### 场景：UserService 不验证 Token，也没有 Gateway

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端发送请求（没有 Token）                                    │
│                                                                 │
│  GET http://localhost:8081/user/1                               │
│  Headers: （没有 Authorization）                                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 直接访问 UserService
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService 收到请求                                            │
│                                                                 │
│  UserService 不验证 Token：                                      │
│                                                                 │
│  @GetMapping("/{id}")                                           │
│  public User queryById(@PathVariable Long id) {                 │
│      // 直接处理业务逻辑，不验证 Token                           │
│      return userService.queryById(id);                          │
│  }                                                              │
│                                                                 │
│  查数据库：SELECT * FROM user WHERE id = 1                       │
│  返回数据：{"id":1,"username":"柳岩","address":"湖南"}           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回数据
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  客户端收到数据                                                  │
│                                                                 │
│  {"id":1,"username":"柳岩","address":"湖南"}                     │
└─────────────────────────────────────────────────────────────────┘
```

**问题：**
- ❌ 任何人都可以访问（没有鉴权）
- ❌ 不知道是谁发的请求
- ❌ 无法控制权限
- ❌ 安全风险极高

**人话：**
- 就像银行不检查身份证，任何人都可以取钱
- 张三可以取李四的钱
- 完全没有安全性

---

## 五、实际场景对比

### 场景：查询用户信息

#### 方案 1：没有 Token 鉴权（不安全）❌

```bash
# 任何人都可以查询任何用户的信息
curl http://localhost:8081/user/1
# 返回：{"id":1,"username":"柳岩","password":"123456","phone":"138xxxx"}

curl http://localhost:8081/user/2
# 返回：{"id":2,"username":"张三","password":"654321","phone":"139xxxx"}

# 问题：
# - 任何人都可以查询
# - 可以看到密码、手机号等敏感信息
# - 没有任何限制
```

---

#### 方案 2：有 Token 鉴权（安全）✅

```bash
# 没有 Token，被拦截
curl http://localhost:10010/user/1
# 返回：401 Unauthorized

# 有 Token，但是 Token 无效，被拦截
curl -H "Authorization: Bearer wrong-token" http://localhost:10010/user/1
# 返回：401 Unauthorized

# 有 Token，Token 有效，可以访问
curl -H "Authorization: Bearer eyJhbGci..." http://localhost:10010/user/1
# 返回：{"id":1,"username":"柳岩","address":"湖南"}

# 优点：
# - 必须有 Token 才能访问
# - Token 无效会被拦截
# - 知道是谁发的请求
# - 可以控制权限
```

---

### 场景：修改用户信息

#### 方案 1：没有 Token 鉴权（不安全）❌

```bash
# 任何人都可以修改任何用户的信息
curl -X PUT http://localhost:8081/user/1 \
  -d '{"username":"黑客","password":"hacked"}'
# 返回：修改成功

# 问题：
# - 张三可以修改李四的信息
# - 黑客可以修改任何人的信息
# - 完全没有安全性
```

---

#### 方案 2：有 Token 鉴权（安全）✅

```bash
# 没有 Token，被拦截
curl -X PUT http://localhost:10010/user/1 \
  -d '{"username":"黑客","password":"hacked"}'
# 返回：401 Unauthorized

# 有 Token，但是只能修改自己的信息
curl -X PUT http://localhost:10010/user/1 \
  -H "Authorization: Bearer eyJhbGci..." \
  -d '{"username":"柳岩2","password":"new-password"}'

# UserService 代码：
@PutMapping("/{id}")
public Result update(
    @PathVariable Long id,
    @RequestHeader("X-User-Id") Long currentUserId,  // 当前用户 ID
    @RequestBody User user
) {
    // 检查：只能修改自己的信息
    if (!id.equals(currentUserId)) {
        return Result.error("无权修改其他用户的信息");
    }
    
    // 修改信息
    userService.update(user);
    return Result.success();
}

# 优点：
# - 必须有 Token 才能访问
# - 只能修改自己的信息
# - 无法修改别人的信息
```

---

## 六、推荐方案

### 推荐：Gateway 统一鉴权，UserService 不鉴权 ✅

```
架构：
  客户端 → Gateway（验证 Token）→ UserService（不验证 Token）

优点：
  ✅ 鉴权代码只写一次
  ✅ UserService 代码简洁
  ✅ 维护方便
  ✅ 性能好

注意：
  ⚠️ 必须禁止外部直接访问 UserService
  ⚠️ 只允许通过 Gateway 访问
```

### 如何禁止外部直接访问 UserService？

#### 方案 1：网络隔离

```
部署架构：
  - Gateway：部署在公网（外部可以访问）
  - UserService：部署在内网（外部无法访问）
  
外部请求：
  客户端 → Gateway（公网）→ UserService（内网）✅
  客户端 → UserService（内网）❌ 无法访问
```

---

#### 方案 2：IP 白名单

```java
// UserService 配置
@Configuration
public class SecurityConfig {
    
    @Bean
    public FilterRegistrationBean<IpFilter> ipFilter() {
        FilterRegistrationBean<IpFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new IpFilter());
        registration.addUrlPatterns("/*");
        return registration;
    }
}

// IP 过滤器
public class IpFilter implements Filter {
    
    // 白名单：只允许 Gateway 的 IP 访问
    private static final List<String> WHITE_LIST = Arrays.asList(
        "192.168.1.100"  // Gateway 的 IP
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String ip = request.getRemoteAddr();
        
        if (!WHITE_LIST.contains(ip)) {
            // 不在白名单，拒绝访问
            response.setStatus(403);
            return;
        }
        
        // 在白名单，放行
        chain.doFilter(request, response);
    }
}
```

---

#### 方案 3：内部 Token

```java
// Gateway 转发请求时，添加内部 Token
@Component
public class InternalTokenFilter implements GlobalFilter {
    
    private static final String INTERNAL_TOKEN = "internal-secret-token-123456";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 添加内部 Token 到请求头
        ServerHttpRequest request = exchange.getRequest().mutate()
            .header("X-Internal-Token", INTERNAL_TOKEN)
            .build();
        
        return chain.filter(exchange.mutate().request(request).build());
    }
}

// UserService 验证内部 Token
@Component
public class InternalTokenFilter implements Filter {
    
    private static final String INTERNAL_TOKEN = "internal-secret-token-123456";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        String token = ((HttpServletRequest) request).getHeader("X-Internal-Token");
        
        if (!INTERNAL_TOKEN.equals(token)) {
            // 没有内部 Token，拒绝访问
            response.setStatus(403);
            return;
        }
        
        // 有内部 Token，放行
        chain.doFilter(request, response);
    }
}
```

---

## 七、总结

### 核心问题

**问题：UserService 用不用 Token 鉴权？**

**答案：取决于架构！**

### 三种架构

| 架构 | Gateway | UserService | 推荐 |
|------|---------|-------------|------|
| 架构 1 | 没有 | 必须鉴权 | ❌ |
| 架构 2 | 统一鉴权 | 不鉴权 | ✅ 推荐 |
| 架构 3 | 鉴权 | 也鉴权 | ⚠️ 双重保险，但性能差 |

### 推荐方案

```
Gateway 统一鉴权，UserService 不鉴权 ✅

优点：
  - 鉴权代码只写一次
  - UserService 代码简洁
  - 维护方便
  - 性能好

注意：
  - 必须禁止外部直接访问 UserService
  - 只允许通过 Gateway 访问
```

### 如果不用 Token 鉴权会怎么样？

```
问题：
  - 任何人都可以访问（没有鉴权）
  - 不知道是谁发的请求
  - 无法控制权限
  - 安全风险极高

就像：
  - 银行不检查身份证
  - 任何人都可以取钱
  - 张三可以取李四的钱
  - 完全没有安全性
```

### 一句话总结

**有 Gateway 的情况下，Gateway 统一鉴权，UserService 不需要鉴权，但要禁止外部直接访问 UserService！没有 Gateway 的情况下，UserService 必须自己鉴权！**
