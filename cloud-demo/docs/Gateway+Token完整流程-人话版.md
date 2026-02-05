# Gateway + Token 完整流程 - 人话版

## 一、系统架构

```
客户端（浏览器）
    ↓
Gateway (端口 10010) - 门卫，检查 Token
    ↓
UserService (端口 8081) - 用户服务
OrderService (端口 8082) - 订单服务
```

---

## 二、完整流程（4个步骤）

### 步骤 1：用户登录（生成 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  用户输入：                                                      │
│    账号：admin                                                   │
│    密码：123456                                                  │
│    点击"登录"                                                    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ POST /user/login
                            │ {"username":"admin","password":"123456"}
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010)                                      │
│                                                                 │
│  收到登录请求                                                    │
│  路径：/user/login                                               │
│  匹配路由：/user/** → userservice                                │
│  转发到 UserService                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService (localhost:8081)                                   │
│                                                                 │
│  1. 验证账号密码                                                 │
│     查数据库：admin 存在吗？密码对吗？                            │
│     ✅ 验证通过                                                  │
│                                                                 │
│  2. 生成 Token                                                  │
│     Token = JWT.生成(userId=1, username=admin, 密钥)            │
│     Token = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...             │
│                                                                 │
│  3. 返回 Token                                                  │
│     {"code":200, "token":"eyJhbGciOiJIUzI1NiIsInR5cCI6..."}    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回 Token
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway                                                        │
│                                                                 │
│  转发响应给客户端                                                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回 Token
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  收到 Token：eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...            │
│                                                                 │
│  保存 Token：                                                    │
│  localStorage.setItem('token', token)                           │
│                                                                 │
│  就像拿到了一张通行证                                            │
└─────────────────────────────────────────────────────────────────┘
```

**人话总结：**
1. 用户输入账号密码
2. Gateway 转发到 UserService
3. UserService 验证成功，生成 Token（通行证）
4. 返回 Token 给用户
5. 用户保存 Token

---

### 步骤 2：访问用户信息（Gateway 验证 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  用户点击"个人中心"                                              │
│                                                                 │
│  取出 Token：                                                    │
│  token = localStorage.getItem('token')                          │
│                                                                 │
│  发送请求：                                                      │
│  GET /user/info                                                 │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...│
│                                                                 │
│  就像出示通行证                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 带 Token 的请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010) - 门卫                                │
│                                                                 │
│  AuthorizeFilter（鉴权过滤器）：                                 │
│                                                                 │
│  1. 检查 Token                                                  │
│     有 Token 吗？                                                │
│     ✅ 有：Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI...  │
│                                                                 │
│  2. 验证 Token                                                  │
│     用密钥重新签名，对比是否一致                                  │
│     ✅ 签名一致，Token 是真的                                    │
│                                                                 │
│  3. 检查过期时间                                                │
│     Token 过期了吗？                                             │
│     ✅ 没过期                                                    │
│                                                                 │
│  4. 提取用户信息                                                │
│     从 Token 中读取：                                            │
│     userId = 1                                                  │
│     username = admin                                            │
│                                                                 │
│  5. 传递给后端服务                                              │
│     在请求头中添加：                                             │
│     X-User-Id: 1                                                │
│     X-Username: admin                                           │
│                                                                 │
│  6. 放行，转发到 UserService                                     │
│                                                                 │
│  就像门卫检查通行证，确认是真的，放行                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求（带用户信息）
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService (localhost:8081)                                   │
│                                                                 │
│  收到请求：                                                      │
│  GET /user/info                                                 │
│  Headers:                                                       │
│    X-User-Id: 1        ← Gateway 传过来的                       │
│    X-Username: admin   ← Gateway 传过来的                       │
│                                                                 │
│  处理请求：                                                      │
│  1. 从请求头获取 userId = 1                                      │
│  2. 查数据库：SELECT * FROM user WHERE id = 1                    │
│  3. 返回用户信息：{"id":1,"username":"柳岩","address":"湖南"}    │
│                                                                 │
│  不需要再验证 Token，Gateway 已经验证过了                         │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回用户信息
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway                                                        │
│                                                                 │
│  转发响应给客户端                                                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回数据
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  收到数据：{"id":1,"username":"柳岩","address":"湖南"}           │
│  显示在页面上                                                    │
└─────────────────────────────────────────────────────────────────┘
```

**人话总结：**
1. 用户带 Token 访问
2. Gateway 验证 Token（门卫检查通行证）
3. 验证通过，提取 userId
4. Gateway 转发请求，把 userId 传给 UserService
5. UserService 根据 userId 查数据库
6. 返回用户信息

---

### 步骤 3：访问订单（Gateway 验证 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  用户点击"我的订单"                                              │
│                                                                 │
│  发送请求：                                                      │
│  GET /order/list                                                │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...│
│                                                                 │
│  还是同一个 Token（同一张通行证）                                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 带 Token 的请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010) - 门卫                                │
│                                                                 │
│  AuthorizeFilter：                                              │
│                                                                 │
│  1. 验证 Token（和上面一样的过程）                               │
│     ✅ Token 是真的                                              │
│     ✅ 没过期                                                    │
│                                                                 │
│  2. 提取用户信息                                                │
│     userId = 1                                                  │
│     username = admin                                            │
│                                                                 │
│  3. 匹配路由                                                    │
│     路径：/order/list                                            │
│     匹配：/order/** → orderservice                               │
│                                                                 │
│  4. 转发到 OrderService                                          │
│     添加请求头：X-User-Id: 1                                     │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求（带用户信息）
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  OrderService (localhost:8082)                                  │
│                                                                 │
│  收到请求：                                                      │
│  GET /order/list                                                │
│  Headers:                                                       │
│    X-User-Id: 1        ← Gateway 传过来的                       │
│                                                                 │
│  处理请求：                                                      │
│  1. 从请求头获取 userId = 1                                      │
│  2. 查数据库：SELECT * FROM orders WHERE user_id = 1             │
│  3. 返回订单列表：[{"id":101,"price":999},{"id":102,"price":888}]│
│                                                                 │
│  不需要验证 Token，Gateway 已经验证过了                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回订单列表
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  收到订单列表，显示在页面上                                       │
└─────────────────────────────────────────────────────────────────┘
```

**人话总结：**
1. 用户带同一个 Token 访问订单
2. Gateway 验证 Token（还是同一个门卫）
3. 验证通过，提取 userId
4. Gateway 转发到 OrderService
5. OrderService 根据 userId 查订单
6. 返回订单列表

---

### 步骤 4：Token 无效的情况

```
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  发送请求（没带 Token 或 Token 无效）                             │
│  GET /user/info                                                 │
│  Headers: （没有 Authorization）                                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 没有 Token
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway - 门卫                                                  │
│                                                                 │
│  AuthorizeFilter：                                              │
│                                                                 │
│  1. 检查 Token                                                  │
│     有 Token 吗？                                                │
│     ❌ 没有！                                                    │
│                                                                 │
│  2. 拒绝请求                                                    │
│     返回 401 Unauthorized                                       │
│     不转发到后端服务                                             │
│                                                                 │
│  就像门卫：没有通行证，不让进！                                   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回 401
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  收到 401 错误                                                   │
│  跳转到登录页面                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**人话总结：**
1. 用户没带 Token 或 Token 无效
2. Gateway 检查：没有通行证！
3. 拒绝请求，返回 401
4. 不转发到后端服务
5. 浏览器跳转到登录页

---

## 三、完整对比图

### 没有 Token 的情况

```
客户端 → Gateway → UserService
         ↑
         │ 每次都要验证账号密码
         │ 麻烦！
```

### 有 Token 的情况

```
第 1 次（登录）：
  客户端 → Gateway → UserService
           验证账号密码 → 生成 Token → 返回 Token

第 2 次（访问用户信息）：
  客户端（带 Token）→ Gateway（验证 Token）→ UserService
                      ↑
                      │ 只验证 Token，不验证密码
                      │ 方便！

第 3 次（访问订单）：
  客户端（带 Token）→ Gateway（验证 Token）→ OrderService
                      ↑
                      │ 还是同一个 Token
                      │ 方便！
```

---

## 四、Gateway 的作用（人话版）

### 1. 统一验证 Token

```
没有 Gateway：
  客户端 → UserService（验证 Token）
  客户端 → OrderService（验证 Token）
  客户端 → ProductService（验证 Token）
  
  问题：每个服务都要写验证 Token 的代码，重复！

有 Gateway：
  客户端 → Gateway（验证 Token）→ UserService
  客户端 → Gateway（验证 Token）→ OrderService
  客户端 → Gateway（验证 Token）→ ProductService
  
  优点：只在 Gateway 验证一次，后端服务不用管！
```

### 2. 提取用户信息

```
Gateway 验证 Token 后：
  1. 提取 userId = 1
  2. 添加到请求头：X-User-Id: 1
  3. 转发给后端服务

后端服务：
  直接从请求头获取 userId
  不需要再解析 Token
  代码更简单！
```

### 3. 统一拦截

```
Token 无效：
  Gateway 直接返回 401
  不转发到后端服务
  节省后端资源！
```

---

## 五、代码对应关系

### Gateway - 验证 Token

```java
// gateway/src/main/java/cn/itcast/gateway/AuthorizeFilter.java

@Component
public class AuthorizeFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 获取 Token
        String token = exchange.getRequest()
            .getHeaders()
            .getFirst("Authorization");
        
        if (token == null) {
            // 没有 Token，返回 401
            return unauthorized(exchange);
        }
        
        token = token.replace("Bearer ", "");
        
        try {
            // 2. 验证 Token
            Claims claims = JwtUtil.parseToken(token);
            
            // 3. 提取用户信息
            Integer userId = claims.get("userId", Integer.class);
            String username = claims.get("username", String.class);
            
            // 4. 添加到请求头
            ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-User-Id", userId.toString())
                .header("X-Username", username)
                .build();
            
            // 5. 放行
            return chain.filter(exchange.mutate().request(request).build());
            
        } catch (Exception e) {
            // Token 无效，返回 401
            return unauthorized(exchange);
        }
    }
}
```

### UserService - 生成 Token

```java
// user-service/src/main/java/cn/itcast/user/web/UserController.java

@PostMapping("/login")
public Map<String, Object> login(String username, String password) {
    // 1. 验证账号密码
    if ("admin".equals(username) && "123456".equals(password)) {
        
        // 2. 生成 Token
        String token = JwtUtil.generateToken(1L, username);
        
        // 3. 返回 Token
        return Map.of("code", 200, "token", token);
    }
    
    return Map.of("code", 401, "message", "账号或密码错误");
}
```

### UserService - 使用用户信息

```java
@GetMapping("/info")
public Map<String, Object> getUserInfo(@RequestHeader("X-User-Id") Integer userId) {
    // Gateway 已经验证过 Token，并把 userId 传过来了
    // 直接使用 userId 查询
    User user = userService.queryById(userId);
    return Map.of("code", 200, "data", user);
}
```

### OrderService - 使用用户信息

```java
@GetMapping("/list")
public Map<String, Object> getOrderList(@RequestHeader("X-User-Id") Integer userId) {
    // Gateway 已经验证过 Token，并把 userId 传过来了
    // 直接使用 userId 查询订单
    List<Order> orders = orderService.getByUserId(userId);
    return Map.of("code", 200, "data", orders);
}
```

---

## 六、总结（一句话）

**完整流程：**
1. **登录**：用户输入账号密码 → Gateway 转发 → UserService 验证并生成 Token → 返回 Token
2. **访问**：用户带 Token 访问 → Gateway 验证 Token 并提取 userId → 转发给 UserService/OrderService → 后端服务使用 userId 处理请求

**Gateway 的作用：**
- 统一验证 Token（门卫）
- 提取用户信息传给后端
- Token 无效直接拦截，不浪费后端资源

**就像：**
- Token = 通行证
- Gateway = 门卫（检查通行证）
- UserService/OrderService = 办事大厅（不用再检查，直接办事）
