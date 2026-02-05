# Token 完整应用流程 - 人话版

## 一、Token 是用来干什么的？

### 1.1 Token 的作用

**Token = 身份证明**

```
就像你去银行办业务：
  1. 第一次去：出示身份证，银行给你办卡
  2. 以后去：刷卡就行，不用每次都出示身份证

Token 也一样：
  1. 第一次：输入账号密码登录，服务器给你 Token
  2. 以后：带着 Token 访问，不用每次都输入密码
```

### 1.2 使用场景

**场景 1：用户登录后访问个人信息**
```
没有 Token（每次都要登录）：
  访问个人信息 → 输入账号密码 → 返回信息
  修改个人信息 → 输入账号密码 → 修改成功
  查看订单 → 输入账号密码 → 返回订单
  
  问题：太麻烦了！

有 Token（登录一次就够了）：
  登录 → 获得 Token → 保存 Token
  访问个人信息 → 带上 Token → 返回信息
  修改个人信息 → 带上 Token → 修改成功
  查看订单 → 带上 Token → 返回订单
  
  优点：方便！
```

**场景 2：区分不同用户**
```
用户 A 登录：
  获得 Token A（里面包含 userId=1）
  
用户 B 登录：
  获得 Token B（里面包含 userId=2）

用户 A 访问个人信息：
  带上 Token A → 服务器解析 → userId=1 → 返回用户 A 的信息

用户 B 访问个人信息：
  带上 Token B → 服务器解析 → userId=2 → 返回用户 B 的信息
```

**场景 3：权限控制**
```
普通用户登录：
  Token 里包含：userId=1, role=USER
  访问 /admin/delete → Gateway 检查 Token → role=USER → 拒绝！

管理员登录：
  Token 里包含：userId=2, role=ADMIN
  访问 /admin/delete → Gateway 检查 Token → role=ADMIN → 允许！
```

## 二、Token 是如何生成的？

### 2.1 生成过程（人话版）

```
第 1 步：用户登录
  用户输入：username=admin, password=123456
  
第 2 步：服务器验证
  查数据库：这个用户存在吗？密码对吗？
  验证通过！
  
第 3 步：准备用户信息
  userId: 1
  username: admin
  role: ADMIN
  过期时间: 2小时后
  
第 4 步：生成 Token
  把用户信息 + 密钥 → 加密 → 生成 Token
  Token = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjF9.xxx
  
第 5 步：返回 Token
  返回给用户：{"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}
```

### 2.2 Token 的结构

```
Token = 3部分用 . 连接

eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjF9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV

第1部分：说明书
  {"alg":"HS256","typ":"JWT"}
  说明：这是 JWT，用 HS256 算法

第2部分：用户信息
  {"userId":1,"username":"admin","role":"ADMIN","exp":1706000000}
  说明：用户 ID 是 1，用户名是 admin，角色是管理员

第3部分：签名（防伪标记）
  用密钥对前两部分签名，防止被篡改
```

## 三、Token 是如何解析的？

### 3.1 解析过程（人话版）

```
第 1 步：收到 Token
  客户端发来：Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  
第 2 步：拆分 Token
  按 . 分成 3 部分
  
第 3 步：验证签名
  用密钥重新计算签名，看是否和 Token 里的签名一致
  一致 → Token 是真的，没被改过
  不一致 → Token 是假的，拒绝！
  
第 4 步：检查过期时间
  看 Token 里的过期时间，过期了吗？
  没过期 → 继续
  过期了 → 拒绝！
  
第 5 步：提取用户信息
  从 Token 里读出：userId=1, username=admin, role=ADMIN
  
第 6 步：使用用户信息
  知道是哪个用户了，可以处理请求了
```

### 3.2 验证原理（防篡改）

```
为什么黑客改不了 Token？

原始 Token：
  用户信息：userId=1
  签名：ABC123（用密钥计算出来的）

黑客想改成：
  用户信息：userId=999（改成别人的ID）
  签名：ABC123（还是原来的签名）

服务器验证：
  1. 读取用户信息：userId=999
  2. 用密钥重新计算签名：得到 XYZ789
  3. 对比：ABC123 ≠ XYZ789
  4. 结论：签名对不上，Token 被改过了！拒绝！

为什么黑客算不出正确的签名？
  因为黑客不知道密钥！
  只有服务器知道密钥
```

## 四、在 cloud-demo 项目中的具体应用

### 4.1 项目架构

```
客户端（浏览器/APP）
    ↓
Gateway (端口 10010)
    ↓
UserService (端口 8081) / OrderService (端口 8082)
```

### 4.2 完整流程（带 Token）

#### 步骤 1：用户登录（生成 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端（浏览器）                                                 │
│                                                                 │
│  用户输入：                                                      │
│    账号：admin                                                   │
│    密码：123456                                                  │
│                                                                 │
│  点击"登录"按钮                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ POST /user/login
                            │ Body: {"username":"admin","password":"123456"}
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010)                                      │
│                                                                 │
│  收到登录请求，转发到 UserService                                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService (localhost:8081)                                   │
│                                                                 │
│  @PostMapping("/login")                                         │
│  public Result login(String username, String password) {        │
│      // 1. 验证用户名密码                                        │
│      User user = userMapper.findByUsername(username);           │
│      if (!password.equals(user.getPassword())) {                │
│          return Result.error("密码错误");                        │
│      }                                                           │
│                                                                 │
│      // 2. 生成 Token                                           │
│      String token = Jwts.builder()                              │
│          .claim("userId", user.getId())        // userId=1      │
│          .claim("username", user.getUsername()) // username=admin│
│          .claim("role", user.getRole())        // role=ADMIN    │
│          .setExpiration(new Date(System.currentTimeMillis()     │
│              + 2 * 60 * 60 * 1000))           // 2小时后过期    │
│          .signWith(SignatureAlgorithm.HS256,                    │
│              "my-secret-key-123456")          // 用密钥签名      │
│          .compact();                                            │
│                                                                 │
│      // 3. 返回 Token                                           │
│      return Result.success(token);                              │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回 Token
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  客户端                                                          │
│                                                                 │
│  收到响应：                                                      │
│  {                                                              │
│    "code": 200,                                                 │
│    "message": "登录成功",                                        │
│    "data": {                                                    │
│      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."        │
│    }                                                            │
│  }                                                              │
│                                                                 │
│  保存 Token：                                                    │
│  localStorage.setItem('token', token);                          │
└─────────────────────────────────────────────────────────────────┘
```

#### 步骤 2：访问个人信息（使用 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端                                                          │
│                                                                 │
│  用户点击"个人中心"                                              │
│                                                                 │
│  从 localStorage 取出 Token：                                    │
│  token = localStorage.getItem('token');                         │
│                                                                 │
│  发送请求：                                                      │
│  GET /user/info                                                 │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...│
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 带着 Token 的请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010)                                      │
│                                                                 │
│  AuthorizeFilter（鉴权过滤器）：                                 │
│                                                                 │
│  public Mono<Void> filter(ServerWebExchange exchange, ...) {    │
│      // 1. 获取 Token                                           │
│      String token = exchange.getRequest()                       │
│          .getHeaders()                                          │
│          .getFirst("Authorization");                            │
│                                                                 │
│      if (token == null || !token.startsWith("Bearer ")) {       │
│          // 没有 Token，返回 401                                 │
│          return unauthorized();                                 │
│      }                                                           │
│                                                                 │
│      token = token.substring(7); // 去掉 "Bearer "              │
│                                                                 │
│      try {                                                      │
│          // 2. 验证 Token                                       │
│          Claims claims = Jwts.parser()                          │
│              .setSigningKey("my-secret-key-123456")             │
│              .parseClaimsJws(token)                             │
│              .getBody();                                        │
│                                                                 │
│          // 3. 提取用户信息                                     │
│          Integer userId = claims.get("userId", Integer.class);  │
│          String username = claims.get("username", String.class);│
│          String role = claims.get("role", String.class);        │
│                                                                 │
│          // 4. 将用户信息传递给后端服务                          │
│          ServerHttpRequest request = exchange.getRequest()      │
│              .mutate()                                          │
│              .header("X-User-Id", userId.toString())            │
│              .header("X-Username", username)                    │
│              .header("X-Role", role)                            │
│              .build();                                          │
│                                                                 │
│          // 5. 放行，转发到 UserService                         │
│          return chain.filter(exchange.mutate()                  │
│              .request(request).build());                        │
│                                                                 │
│      } catch (Exception e) {                                    │
│          // Token 无效或过期，返回 401                           │
│          return unauthorized();                                 │
│      }                                                           │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求（带上用户信息）
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService (localhost:8081)                                   │
│                                                                 │
│  @GetMapping("/info")                                           │
│  public Result getUserInfo(                                     │
│      @RequestHeader("X-User-Id") Integer userId) {              │
│                                                                 │
│      // 从请求头获取用户 ID（Gateway 传过来的）                  │
│      // userId = 1                                              │
│                                                                 │
│      // 查询用户信息                                            │
│      User user = userMapper.findById(userId);                   │
│                                                                 │
│      // 返回用户信息                                            │
│      return Result.success(user);                               │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回用户信息
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  客户端                                                          │
│                                                                 │
│  收到数据：                                                      │
│  {                                                              │
│    "userId": 1,                                                 │
│    "username": "admin",                                         │
│    "email": "admin@example.com"                                 │
│  }                                                              │
│                                                                 │
│  显示在页面上                                                    │
└─────────────────────────────────────────────────────────────────┘
```

#### 步骤 3：访问订单（使用同一个 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端                                                          │
│                                                                 │
│  用户点击"我的订单"                                              │
│                                                                 │
│  发送请求：                                                      │
│  GET /order/list                                                │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...│
│    （还是同一个 Token）                                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway                                                        │
│                                                                 │
│  验证 Token → 提取 userId=1 → 转发到 OrderService               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  OrderService (localhost:8082)                                  │
│                                                                 │
│  @GetMapping("/list")                                           │
│  public Result getOrderList(                                    │
│      @RequestHeader("X-User-Id") Integer userId) {              │
│                                                                 │
│      // userId = 1（从 Gateway 传过来的）                        │
│                                                                 │
│      // 查询这个用户的所有订单                                   │
│      List<Order> orders = orderMapper.findByUserId(userId);     │
│                                                                 │
│      return Result.success(orders);                             │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 关键代码位置

#### UserService - 生成 Token

```java
// user-service/src/main/java/cn/itcast/user/controller/UserController.java

@RestController
@RequestMapping("/user")
public class UserController {
    
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        // 验证用户名密码
        User user = userService.login(request.getUsername(), request.getPassword());
        
        // 生成 Token
        String token = JwtUtil.generateToken(user);
        
        return Result.success(token);
    }
    
    @GetMapping("/info")
    public Result getUserInfo(@RequestHeader("X-User-Id") Integer userId) {
        User user = userService.getById(userId);
        return Result.success(user);
    }
}
```

#### Gateway - 验证 Token

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
        
        if (token == null || !token.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        
        token = token.substring(7);
        
        try {
            // 2. 验证 Token
            Claims claims = JwtUtil.parseToken(token);
            
            // 3. 提取用户信息
            Integer userId = claims.get("userId", Integer.class);
            String username = claims.get("username", String.class);
            
            // 4. 传递给后端服务
            ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-User-Id", userId.toString())
                .header("X-Username", username)
                .build();
            
            // 5. 放行
            return chain.filter(exchange.mutate().request(request).build());
            
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }
}
```

#### OrderService - 使用用户信息

```java
// order-service/src/main/java/cn/itcast/order/controller/OrderController.java

@RestController
@RequestMapping("/order")
public class OrderController {
    
    @GetMapping("/list")
    public Result getOrderList(@RequestHeader("X-User-Id") Integer userId) {
        // Gateway 已经验证过 Token，并把 userId 传过来了
        // 直接使用 userId 查询订单
        List<Order> orders = orderService.getByUserId(userId);
        return Result.success(orders);
    }
}
```

## 五、总结

### 5.1 Token 的作用

1. **身份证明**：证明你是谁
2. **免密登录**：登录一次，后续不用再输密码
3. **权限控制**：不同角色有不同权限

### 5.2 Token 的生成

1. 用户登录 → 验证成功
2. 准备用户信息（userId, username, role）
3. 用密钥签名 → 生成 Token
4. 返回给客户端

### 5.3 Token 的验证

1. 客户端带 Token 访问
2. Gateway 拆分 Token
3. 用密钥重新签名，验证是否一致
4. 检查是否过期
5. 提取用户信息
6. 转发给后端服务

### 5.4 在 cloud-demo 中的应用

```
登录流程：
  客户端 → Gateway → UserService
  UserService 生成 Token → 返回给客户端
  客户端保存 Token

访问流程：
  客户端带 Token → Gateway 验证 Token → 提取 userId
  Gateway 转发（带 userId）→ UserService/OrderService
  后端服务使用 userId 处理请求
```

**一句话总结：** Token 是用户登录后获得的"通行证"，包含用户信息和签名，客户端每次请求都带上它，Gateway 验证通过后提取用户信息并转发给后端服务，后端服务根据用户信息处理请求。
