# Gateway 统一鉴权 - 超简单版

## 一、人话版（一句话）

**Gateway 就像小区保安：**
- 所有人进小区都要先过保安（Gateway）
- 保安检查门禁卡（Token）
- 卡有效就放行，卡无效就拦截
- 进了小区后，各个楼栋（UserService、OrderService）不用再检查了

---

## 二、为什么需要统一鉴权？

### 方案 1：每个服务自己鉴权（不推荐）

```
客户端 → UserService → 检查 Token ✅
客户端 → OrderService → 检查 Token ✅
客户端 → ProductService → 检查 Token ✅
```

**问题：**
- 每个服务都要写鉴权代码（重复劳动）
- 鉴权逻辑改了，所有服务都要改
- 代码冗余，维护困难

### 方案 2：Gateway 统一鉴权（推荐）✅

```
客户端 → Gateway → 检查 Token ✅ → UserService
                                 → OrderService
                                 → ProductService
```

**优点：**
- 鉴权代码只写一次（在 Gateway）
- 鉴权逻辑改了，只需要改 Gateway
- 后端服务不用关心鉴权，专注业务逻辑

---

## 三、完整流程图（以 UserService 为例）

### 场景 1：有效 Token（放行）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：客户端发送请求（带 Token）                             │
│                                                                 │
│  浏览器发送：                                                    │
│    GET http://localhost:10010/user/1?authorization=admin        │
│                                                                 │
│  分解：                                                          │
│    地址：http://localhost:10010/user/1                          │
│    参数：authorization=admin  ← 这是 Token（门禁卡）            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 带着 Token 来了
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Gateway 收到请求                                       │
│                                                                 │
│  Gateway (端口 10010) 收到：                                     │
│    路径：/user/1                                                │
│    参数：authorization=admin                                    │
│                                                                 │
│  Gateway 想：先别急着转发，我得先检查 Token                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：AuthorizeFilter 鉴权过滤器（保安检查门禁卡）            │
│                                                                 │
│  AuthorizeFilter 代码：                                          │
│    // 1. 获取请求参数                                           │
│    String auth = params.getFirst("authorization");              │
│                                                                 │
│    // 2. 检查参数值是否等于 admin                               │
│    if ("admin".equals(auth)) {                                  │
│        // 3. 是，放行                                           │
│        return chain.filter(exchange);                           │
│    }                                                            │
│                                                                 │
│  检查过程：                                                      │
│    获取参数：authorization = "admin"                            │
│    判断：admin == admin？                                       │
│    结果：✅ 相等！                                               │
│                                                                 │
│  Gateway 想：Token 有效，放行！                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ✅ 鉴权通过
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：匹配路由规则                                           │
│                                                                 │
│  Gateway 查看配置：                                              │
│    routes:                                                      │
│      - id: user-service                                         │
│        uri: lb://userservice                                    │
│        predicates:                                              │
│          - Path=/user/**                                        │
│                                                                 │
│  匹配：/user/1 符合 /user/** ✅                                  │
│  目标：lb://userservice                                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：查询 Nacos                                             │
│                                                                 │
│  Gateway → Nacos：userservice 在哪？                             │
│  Nacos → Gateway：在 localhost:8081                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：转发请求到 UserService                                 │
│                                                                 │
│  Gateway 转发：                                                  │
│    GET http://localhost:8081/user/1                             │
│                                                                 │
│  注意：                                                          │
│    不需要再带 authorization 参数了                              │
│    因为已经在 Gateway 验证过了                                   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 7 步：UserService 处理请求                                   │
│                                                                 │
│  UserService (端口 8081) 收到：                                  │
│    GET /user/1                                                  │
│                                                                 │
│  UserService 想：                                                │
│    请求能到我这里，说明 Gateway 已经验证过了                     │
│    我不需要再验证 Token，直接处理业务逻辑                        │
│                                                                 │
│  查数据库：SELECT * FROM user WHERE id = 1                       │
│  返回数据：{"id":1,"username":"柳岩","address":"湖南"}           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回数据
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 8 步：Gateway 转发响应                                       │
│                                                                 │
│  Gateway → 客户端：{"id":1,"username":"柳岩","address":"湖南"}   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 9 步：客户端收到数据                                         │
│                                                                 │
│  浏览器显示：{"id":1,"username":"柳岩","address":"湖南"}         │
└─────────────────────────────────────────────────────────────────┘
```

---

### 场景 2：无效 Token（拦截）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：客户端发送请求（Token 错误）                           │
│                                                                 │
│  浏览器发送：                                                    │
│    GET http://localhost:10010/user/1?authorization=wrong        │
│                                                                 │
│  分解：                                                          │
│    地址：http://localhost:10010/user/1                          │
│    参数：authorization=wrong  ← Token 错误！                    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 带着错误的 Token 来了
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Gateway 收到请求                                       │
│                                                                 │
│  Gateway (端口 10010) 收到：                                     │
│    路径：/user/1                                                │
│    参数：authorization=wrong                                    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：AuthorizeFilter 鉴权过滤器（保安检查门禁卡）            │
│                                                                 │
│  AuthorizeFilter 代码：                                          │
│    // 1. 获取请求参数                                           │
│    String auth = params.getFirst("authorization");              │
│                                                                 │
│    // 2. 检查参数值是否等于 admin                               │
│    if ("admin".equals(auth)) {                                  │
│        // 3. 是，放行                                           │
│        return chain.filter(exchange);                           │
│    }                                                            │
│    // 4. 否，拦截                                               │
│    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);│
│    return exchange.getResponse().setComplete();                 │
│                                                                 │
│  检查过程：                                                      │
│    获取参数：authorization = "wrong"                            │
│    判断：admin == wrong？                                       │
│    结果：❌ 不相等！                                             │
│                                                                 │
│  Gateway 想：Token 无效，拦截！                                  │
│  Gateway 返回：401 Unauthorized（未授权）                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ❌ 鉴权失败
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：直接返回错误，不转发到 UserService                     │
│                                                                 │
│  Gateway → 客户端：                                              │
│    状态码：401 Unauthorized                                     │
│    消息：未授权                                                  │
│                                                                 │
│  UserService 根本收不到请求！                                    │
│  （被 Gateway 拦截了）                                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：客户端收到错误                                         │
│                                                                 │
│  浏览器显示：401 Unauthorized                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

### 场景 3：没有 Token（拦截）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：客户端发送请求（没有 Token）                           │
│                                                                 │
│  浏览器发送：                                                    │
│    GET http://localhost:10010/user/1                            │
│                                                                 │
│  注意：没有 authorization 参数！                                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 没带 Token
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Gateway 收到请求                                       │
│                                                                 │
│  Gateway (端口 10010) 收到：                                     │
│    路径：/user/1                                                │
│    参数：无                                                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：AuthorizeFilter 鉴权过滤器（保安检查门禁卡）            │
│                                                                 │
│  检查过程：                                                      │
│    获取参数：authorization = null（没有这个参数）               │
│    判断：admin == null？                                        │
│    结果：❌ 不相等！                                             │
│                                                                 │
│  Gateway 想：没有 Token，拦截！                                  │
│  Gateway 返回：401 Unauthorized（未授权）                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ❌ 鉴权失败
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：直接返回错误                                           │
│                                                                 │
│  Gateway → 客户端：401 Unauthorized                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、简化版流程图

### 有效 Token（放行）

```
客户端
  │
  │ ① 发送：GET /user/1?authorization=admin
  ↓
Gateway (10010)
  │
  │ ② 收到请求
  │ ③ AuthorizeFilter 检查 Token
  │    authorization = "admin"
  │    admin == admin？✅ 是的！
  │ ④ 鉴权通过，放行
  │ ⑤ 匹配路由：/user/** → userservice
  │ ⑥ 查询 Nacos：userservice 在 localhost:8081
  │ ⑦ 转发：GET http://localhost:8081/user/1
  ↓
UserService (8081)
  │
  │ ⑧ 处理请求（不需要再验证 Token）
  │ ⑨ 返回数据
  ↓
Gateway
  │
  │ ⑩ 转发响应
  ↓
客户端
```

### 无效 Token（拦截）

```
客户端
  │
  │ ① 发送：GET /user/1?authorization=wrong
  ↓
Gateway (10010)
  │
  │ ② 收到请求
  │ ③ AuthorizeFilter 检查 Token
  │    authorization = "wrong"
  │    admin == wrong？❌ 不是！
  │ ④ 鉴权失败，拦截
  │ ⑤ 返回：401 Unauthorized
  ↓
客户端
  │
  │ ⑥ 显示错误：401 Unauthorized
  
UserService 根本收不到请求！
```

---

## 五、核心代码详解

### AuthorizeFilter.java（鉴权过滤器）

```java
@Component
public class AuthorizeFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取请求参数
        ServerHttpRequest request = exchange.getRequest();
        MultiValueMap<String, String> params = request.getQueryParams();
        
        // 2. 获取参数中的 authorization 参数
        String auth = params.getFirst("authorization");
        
        // 3. 判断参数值是否等于 admin
        if ("admin".equals(auth)) {
            // 4. 是，放行
            return chain.filter(exchange);
        }
        
        // 5. 否，拦截
        // 5.1 设置状态码为 401
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        // 5.2 拦截请求
        return exchange.getResponse().setComplete();
    }
}
```

**人话翻译：**

```java
// 这是一个过滤器，所有请求都会经过这里

public Mono<Void> filter(...) {
    // 1. 获取请求参数
    //    例如：/user/1?authorization=admin
    //    获取到：authorization = "admin"
    String auth = params.getFirst("authorization");
    
    // 2. 检查 Token 是否正确
    if ("admin".equals(auth)) {
        // Token 正确，放行
        return chain.filter(exchange);  // 继续往下走
    }
    
    // Token 错误或没有 Token，拦截
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);  // 返回 401
    return exchange.getResponse().setComplete();  // 结束请求
}
```

---

## 六、对比图

### 没有 Gateway 统一鉴权

```
客户端 → UserService
           │
           ├─ 检查 Token ✅
           ├─ 处理业务逻辑
           └─ 返回数据

客户端 → OrderService
           │
           ├─ 检查 Token ✅（重复代码）
           ├─ 处理业务逻辑
           └─ 返回数据

客户端 → ProductService
           │
           ├─ 检查 Token ✅（重复代码）
           ├─ 处理业务逻辑
           └─ 返回数据
```

**问题：**
- 每个服务都要写鉴权代码
- 代码重复，维护困难

---

### 有 Gateway 统一鉴权（推荐）

```
客户端 → Gateway
           │
           ├─ 检查 Token ✅（只在这里检查一次）
           │
           ├─ Token 有效？
           │   ├─ 是 → 转发到后端服务
           │   └─ 否 → 返回 401
           │
           ├→ UserService（不需要检查 Token）
           ├→ OrderService（不需要检查 Token）
           └→ ProductService（不需要检查 Token）
```

**优点：**
- 鉴权代码只写一次
- 后端服务专注业务逻辑
- 维护简单

---

## 七、实际测试

### 测试 1：有效 Token（放行）

```bash
# 带正确的 Token
curl "http://localhost:10010/user/1?authorization=admin"

# 结果：
{"id":1,"username":"柳岩","address":"湖南省衡阳市"}

# ✅ 成功！
```

### 测试 2：无效 Token（拦截）

```bash
# 带错误的 Token
curl "http://localhost:10010/user/1?authorization=wrong"

# 结果：
401 Unauthorized

# ❌ 被拦截！
```

### 测试 3：没有 Token（拦截）

```bash
# 不带 Token
curl "http://localhost:10010/user/1"

# 结果：
401 Unauthorized

# ❌ 被拦截！
```

### 测试 4：直接访问 UserService（绕过 Gateway）

```bash
# 直接访问 UserService，不经过 Gateway
curl "http://localhost:8081/user/1"

# 结果：
{"id":1,"username":"柳岩","address":"湖南省衡阳市"}

# ✅ 成功！
# 因为没有经过 Gateway，所以没有鉴权
```

**注意：**
- 直接访问 UserService 可以绕过鉴权
- 实际项目中，应该禁止外部直接访问 UserService
- 只允许通过 Gateway 访问

---

## 八、真实场景（JWT Token）

### 当前项目（简化版）

```
客户端 → Gateway
           │
           ├─ 检查：authorization == "admin"？
           │   ├─ 是 → 放行
           │   └─ 否 → 拦截
```

**问题：**
- Token 是固定的（"admin"）
- 不安全，任何人都可以用

---

### 真实项目（JWT Token）

```
客户端 → Gateway
           │
           ├─ 检查：Token 是否有效？
           │   ├─ 验证签名
           │   ├─ 检查过期时间
           │   ├─ 解析用户信息
           │   │
           │   ├─ 有效 → 放行
           │   └─ 无效 → 拦截
```

**JWT Token 示例：**

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoi5p2z5bKpIiwiaWF0IjoxNjAwMDAwMDAwLCJleHAiOjE2MDAwMDM2MDB9.xxxxx

分解：
  Header（头部）：{"alg":"HS256","typ":"JWT"}
  Payload（载荷）：{"userId":1,"username":"柳岩","iat":1600000000,"exp":1600003600}
  Signature（签名）：xxxxx
```

**验证过程：**

```java
// 1. 解析 Token
Claims claims = Jwts.parser()
    .setSigningKey("secret-key")  // 密钥
    .parseClaimsJws(token)
    .getBody();

// 2. 检查过期时间
if (claims.getExpiration().before(new Date())) {
    // Token 过期，拦截
    return 401;
}

// 3. 获取用户信息
Long userId = claims.get("userId", Long.class);
String username = claims.get("username", String.class);

// 4. 放行
return chain.filter(exchange);
```

---

## 九、总结

### 核心流程（9步）

1. **客户端发送请求**：带 Token（`authorization=admin`）
2. **Gateway 收到请求**
3. **AuthorizeFilter 检查 Token**：`admin == admin`？
4. **Token 有效**：放行 ✅
5. **匹配路由规则**：`/user/**` → `userservice`
6. **查询 Nacos**：`userservice` 在 `localhost:8081`
7. **转发请求**：`http://localhost:8081/user/1`
8. **UserService 处理**：不需要再验证 Token
9. **返回数据**：通过 Gateway 返回给客户端

### 关键点

- **统一鉴权**：所有请求都在 Gateway 检查 Token
- **一次验证**：Gateway 验证通过后，后端服务不需要再验证
- **拦截无效请求**：Token 无效直接返回 401，不转发到后端
- **简化后端**：后端服务专注业务逻辑，不关心鉴权

### 一句话总结

**Gateway 就像小区保安：所有人进小区都要先过保安检查门禁卡，卡有效就放行，卡无效就拦截，进了小区后各个楼栋不用再检查了。**

---

## 十、常见问题

### Q1：为什么要在 Gateway 统一鉴权？

**A：**
- 避免每个服务都写鉴权代码（代码重复）
- 鉴权逻辑改了，只需要改 Gateway
- 后端服务专注业务逻辑

### Q2：如果直接访问 UserService（绕过 Gateway）怎么办？

**A：**
- 实际项目中，应该禁止外部直接访问后端服务
- 只允许通过 Gateway 访问
- 可以通过网络配置（防火墙）实现

### Q3：当前项目的 Token（authorization=admin）安全吗？

**A：**
- 不安全，这只是教学示例
- 真实项目应该用 JWT Token
- JWT Token 包含签名、过期时间等，更安全

### Q4：AuthorizeFilter 什么时候执行？

**A：**
- 在路由匹配之前执行
- 所有请求都会经过这个过滤器
- Token 无效直接拦截，不会转发到后端

### Q5：后端服务怎么知道是哪个用户发的请求？

**A：**
- Gateway 可以在请求头中添加用户信息
- 例如：`X-User-Id: 1`
- 后端服务从请求头中获取用户信息

**示例代码：**

```java
// Gateway 添加用户信息到请求头
ServerHttpRequest request = exchange.getRequest().mutate()
    .header("X-User-Id", userId.toString())
    .header("X-Username", username)
    .build();

// UserService 获取用户信息
String userId = request.getHeader("X-User-Id");
String username = request.getHeader("X-Username");
```
