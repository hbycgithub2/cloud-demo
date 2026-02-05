# Token 和 Session 的区别 - 人话版

## 一、你的问题

**问题 1：用户信息一定要从 Token 里获取吗？**

**答案：不一定！有两种方案：**
1. **Token 方案**：用户信息存在 Token 里，每次从 Token 解析
2. **Session 方案**：用户信息存在 Redis 里，每次从 Redis 取

---

**问题 2：为什么不直接把用户信息存 Redis，从 Redis 取？**

**答案：可以！这就是 Session 方案！**

---

## 二、两种方案对比

### 方案 1：Session 方案（传统方案）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：用户登录                                               │
│                                                                 │
│  POST /user/login                                               │
│  Body: {"username":"张三","password":"123456"}                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：服务器验证用户名密码                                   │
│                                                                 │
│  查数据库：SELECT * FROM user WHERE username = '张三'           │
│  验证密码：✅ 正确                                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：生成 Session ID，存入 Redis                            │
│                                                                 │
│  // 生成随机 Session ID                                         │
│  String sessionId = UUID.randomUUID().toString();               │
│  // "abc123"                                                    │
│                                                                 │
│  // 把用户信息存入 Redis                                        │
│  redis.set("session:abc123", {                                  │
│      "userId": 1,                                               │
│      "username": "张三",                                        │
│      "role": "user"                                             │
│  });                                                            │
│                                                                 │
│  // 设置过期时间（1 小时）                                      │
│  redis.expire("session:abc123", 3600);                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：返回 Session ID 给客户端                               │
│                                                                 │
│  返回：{"sessionId":"abc123"}                                   │
│                                                                 │
│  客户端保存 Session ID：                                         │
│    localStorage.setItem("sessionId", "abc123");                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：访问接口（带 Session ID）                              │
│                                                                 │
│  POST /cart/add                                                 │
│  Headers:                                                       │
│    Session-Id: abc123  ← 带 Session ID                          │
│  Body:                                                          │
│    {"productId":100,"quantity":2}                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：服务器从 Redis 获取用户信息                            │
│                                                                 │
│  // 从请求头获取 Session ID                                     │
│  String sessionId = request.getHeader("Session-Id");            │
│  // "abc123"                                                    │
│                                                                 │
│  // 从 Redis 获取用户信息                                       │
│  User user = redis.get("session:abc123");                       │
│  // {"userId":1,"username":"张三","role":"user"}                │
│                                                                 │
│  // 获取用户 ID                                                 │
│  Long userId = user.getUserId();  // 1                          │
│                                                                 │
│  // 把商品加到用户 1 的购物车                                    │
│  INSERT INTO cart (user_id, product_id, quantity)               │
│  VALUES (1, 100, 2)                                             │
└─────────────────────────────────────────────────────────────────┘
```

**特点：**
- ✅ 用户信息存在 Redis 里
- ✅ 每次从 Redis 取用户信息
- ✅ Session ID 只是一个随机字符串（"abc123"）
- ✅ 可以随时修改用户信息（改 Redis 就行）
- ❌ 需要访问 Redis（网络开销）
- ❌ Redis 挂了，所有用户都要重新登录

---

### 方案 2：Token 方案（JWT）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：用户登录                                               │
│                                                                 │
│  POST /user/login                                               │
│  Body: {"username":"张三","password":"123456"}                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：服务器验证用户名密码                                   │
│                                                                 │
│  查数据库：SELECT * FROM user WHERE username = '张三'           │
│  验证密码：✅ 正确                                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：生成 JWT Token（用户信息存在 Token 里）                │
│                                                                 │
│  String token = Jwts.builder()                                  │
│      .setSubject("1")              // 用户 ID                   │
│      .claim("username", "张三")    // 用户名                    │
│      .claim("role", "user")        // 角色                      │
│      .setExpiration(new Date() + 1小时)  // 过期时间            │
│      .signWith(SignatureAlgorithm.HS256, "secret-key")  // 签名 │
│      .compact();                                                │
│                                                                 │
│  // 生成的 Token：                                              │
│  // eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.                       │
│  // eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLlvKDkuIkiLCJyb2xlIjoidXNlciJ9.│
│  // xxxxx                                                       │
│                                                                 │
│  // Token 里包含了用户信息！                                    │
│  // 不需要存 Redis                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：返回 Token 给客户端                                    │
│                                                                 │
│  返回：{"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."}      │
│                                                                 │
│  客户端保存 Token：                                              │
│    localStorage.setItem("token", token);                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：访问接口（带 Token）                                   │
│                                                                 │
│  POST /cart/add                                                 │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGci...  ← 带 Token                │
│  Body:                                                          │
│    {"productId":100,"quantity":2}                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：服务器从 Token 解析用户信息                            │
│                                                                 │
│  // 从请求头获取 Token                                          │
│  String token = request.getHeader("Authorization");             │
│                                                                 │
│  // 解析 Token，获取用户信息                                    │
│  Claims claims = Jwts.parser()                                  │
│      .setSigningKey("secret-key")                               │
│      .parseClaimsJws(token)                                     │
│      .getBody();                                                │
│                                                                 │
│  // 从 Token 里获取用户 ID                                      │
│  Long userId = Long.parseLong(claims.getSubject());  // 1       │
│  String username = claims.get("username");  // "张三"           │
│                                                                 │
│  // 不需要访问 Redis！                                          │
│  // 用户信息直接从 Token 里解析出来                             │
│                                                                 │
│  // 把商品加到用户 1 的购物车                                    │
│  INSERT INTO cart (user_id, product_id, quantity)               │
│  VALUES (1, 100, 2)                                             │
└─────────────────────────────────────────────────────────────────┘
```

**特点：**
- ✅ 用户信息存在 Token 里
- ✅ 每次从 Token 解析用户信息
- ✅ 不需要访问 Redis（性能好）
- ✅ 无状态（服务器不需要存储 Session）
- ❌ Token 一旦生成，无法修改（除非重新生成）
- ❌ Token 泄露了，无法立即失效（除非加黑名单）

---

## 三、详细对比

### Session 方案

#### 优点

```
1. 可以随时修改用户信息
   - 用户权限变了，改 Redis 就行
   - 立即生效

2. 可以随时让用户下线
   - 删除 Redis 里的 Session
   - 用户立即下线

3. 可以统计在线用户
   - 查询 Redis 里有多少个 Session
   - 就知道有多少用户在线
```

#### 缺点

```
1. 需要访问 Redis
   - 每次请求都要查 Redis
   - 网络开销

2. Redis 挂了，所有用户都要重新登录
   - 单点故障

3. 分布式部署麻烦
   - 多个服务器要共享 Redis
   - Redis 要做集群
```

#### 代码示例

```java
// 登录接口
@PostMapping("/login")
public Result login(@RequestBody LoginRequest request) {
    // 1. 验证用户名密码
    User user = userService.login(request.getUsername(), request.getPassword());
    if (user == null) {
        return Result.error("用户名或密码错误");
    }
    
    // 2. 生成 Session ID
    String sessionId = UUID.randomUUID().toString();
    
    // 3. 把用户信息存入 Redis
    redisTemplate.opsForValue().set(
        "session:" + sessionId,
        user,
        1,  // 过期时间
        TimeUnit.HOURS
    );
    
    // 4. 返回 Session ID
    return Result.success(sessionId);
}

// 加入购物车接口
@PostMapping("/cart/add")
public Result addCart(
    @RequestHeader("Session-Id") String sessionId,
    @RequestBody CartRequest request
) {
    // 1. 从 Redis 获取用户信息
    User user = redisTemplate.opsForValue().get("session:" + sessionId);
    if (user == null) {
        return Result.error("Session 过期，请重新登录");
    }
    
    // 2. 获取用户 ID
    Long userId = user.getId();
    
    // 3. 把商品加到购物车
    cartService.add(userId, request.getProductId(), request.getQuantity());
    
    return Result.success();
}
```

---

### Token 方案（JWT）

#### 优点

```
1. 不需要访问 Redis
   - 用户信息存在 Token 里
   - 直接解析 Token 就行
   - 性能好

2. 无状态
   - 服务器不需要存储 Session
   - 分布式部署简单

3. 跨域友好
   - Token 可以放在请求头
   - 不依赖 Cookie
```

#### 缺点

```
1. Token 一旦生成，无法修改
   - 用户权限变了，Token 还是旧的
   - 要等 Token 过期后重新生成

2. Token 泄露了，无法立即失效
   - 除非加黑名单（又要用 Redis）

3. Token 体积大
   - Token 里包含用户信息
   - 每次请求都要传输
```

#### 代码示例

```java
// 登录接口
@PostMapping("/login")
public Result login(@RequestBody LoginRequest request) {
    // 1. 验证用户名密码
    User user = userService.login(request.getUsername(), request.getPassword());
    if (user == null) {
        return Result.error("用户名或密码错误");
    }
    
    // 2. 生成 JWT Token
    String token = Jwts.builder()
        .setSubject(user.getId().toString())
        .claim("username", user.getUsername())
        .claim("role", user.getRole())
        .setExpiration(new Date(System.currentTimeMillis() + 3600000))
        .signWith(SignatureAlgorithm.HS256, "secret-key")
        .compact();
    
    // 3. 返回 Token（不需要存 Redis）
    return Result.success(token);
}

// 加入购物车接口
@PostMapping("/cart/add")
public Result addCart(
    @RequestHeader("Authorization") String authHeader,
    @RequestBody CartRequest request
) {
    // 1. 从 Token 解析用户信息
    String token = authHeader.replace("Bearer ", "");
    Claims claims = Jwts.parser()
        .setSigningKey("secret-key")
        .parseClaimsJws(token)
        .getBody();
    
    // 2. 获取用户 ID（不需要访问 Redis）
    Long userId = Long.parseLong(claims.getSubject());
    
    // 3. 把商品加到购物车
    cartService.add(userId, request.getProductId(), request.getQuantity());
    
    return Result.success();
}
```

---

## 四、混合方案（推荐）✅

### 方案：Token + Redis

```
结合两种方案的优点：
  - 用 Token 存基本信息（用户 ID、用户名）
  - 用 Redis 存扩展信息（权限、角色）
```

#### 流程

```
1. 登录：
   - 生成 Token（包含用户 ID、用户名）
   - 把权限信息存 Redis
   - 返回 Token

2. 访问接口：
   - 从 Token 解析用户 ID（不访问 Redis，快）
   - 如果需要权限信息，再查 Redis

3. 修改权限：
   - 只需要改 Redis
   - Token 不需要重新生成
```

#### 代码示例

```java
// 登录接口
@PostMapping("/login")
public Result login(@RequestBody LoginRequest request) {
    User user = userService.login(request.getUsername(), request.getPassword());
    
    // 1. 生成 Token（只包含基本信息）
    String token = Jwts.builder()
        .setSubject(user.getId().toString())
        .claim("username", user.getUsername())
        .setExpiration(new Date(System.currentTimeMillis() + 3600000))
        .signWith(SignatureAlgorithm.HS256, "secret-key")
        .compact();
    
    // 2. 把权限信息存 Redis
    redisTemplate.opsForValue().set(
        "user:permission:" + user.getId(),
        user.getPermissions(),
        1,
        TimeUnit.HOURS
    );
    
    return Result.success(token);
}

// 加入购物车接口（不需要权限）
@PostMapping("/cart/add")
public Result addCart(
    @RequestHeader("Authorization") String authHeader,
    @RequestBody CartRequest request
) {
    // 从 Token 解析用户 ID（不访问 Redis，快）
    String token = authHeader.replace("Bearer ", "");
    Claims claims = Jwts.parser()
        .setSigningKey("secret-key")
        .parseClaimsJws(token)
        .getBody();
    
    Long userId = Long.parseLong(claims.getSubject());
    
    // 直接处理业务逻辑
    cartService.add(userId, request.getProductId(), request.getQuantity());
    
    return Result.success();
}

// 删除用户接口（需要权限）
@DeleteMapping("/user/{id}")
public Result deleteUser(
    @RequestHeader("Authorization") String authHeader,
    @PathVariable Long id
) {
    // 1. 从 Token 解析用户 ID
    String token = authHeader.replace("Bearer ", "");
    Claims claims = Jwts.parser()
        .setSigningKey("secret-key")
        .parseClaimsJws(token)
        .getBody();
    
    Long userId = Long.parseLong(claims.getSubject());
    
    // 2. 从 Redis 获取权限信息（只有需要权限时才访问 Redis）
    List<String> permissions = redisTemplate.opsForValue().get("user:permission:" + userId);
    
    // 3. 检查权限
    if (!permissions.contains("user:delete")) {
        return Result.error("无权删除用户");
    }
    
    // 4. 删除用户
    userService.delete(id);
    
    return Result.success();
}
```

---

## 五、对比表

| 对比项 | Session 方案 | Token 方案 | 混合方案 |
|--------|-------------|-----------|----------|
| 用户信息存储 | Redis | Token | Token（基本）+ Redis（扩展）|
| 性能 | 差（每次查 Redis）| 好（不查 Redis）| 好（大部分不查 Redis）|
| 可修改性 | 好（改 Redis）| 差（要重新生成）| 好（改 Redis）|
| 分布式 | 麻烦（要共享 Redis）| 简单（无状态）| 简单 |
| 单点故障 | 有（Redis 挂了）| 无 | 有（但影响小）|
| 推荐度 | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

---

## 六、总结

### 你的问题

**问题：为什么不直接把用户信息存 Redis，从 Redis 取？**

**答案：可以！这就是 Session 方案！**

### 两种方案

```
Session 方案：
  - 用户信息存 Redis
  - 每次从 Redis 取
  - 优点：可以随时修改
  - 缺点：需要访问 Redis，性能差

Token 方案：
  - 用户信息存 Token
  - 每次从 Token 解析
  - 优点：不需要访问 Redis，性能好
  - 缺点：无法修改
```

### 推荐方案

```
混合方案（Token + Redis）：
  - 基本信息存 Token（用户 ID、用户名）
  - 扩展信息存 Redis（权限、角色）
  - 大部分请求只需要解析 Token（快）
  - 需要权限时才查 Redis
  - 结合两种方案的优点
```

### 一句话总结

**用户信息可以存 Redis（Session 方案），也可以存 Token（JWT 方案）。Session 方案需要每次查 Redis，性能差但可以随时修改；Token 方案不需要查 Redis，性能好但无法修改。推荐混合方案：基本信息存 Token，扩展信息存 Redis，结合两种方案的优点！**
