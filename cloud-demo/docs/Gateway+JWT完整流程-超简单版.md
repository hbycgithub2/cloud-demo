# Gateway + JWT 完整流程 - 超简单版

## 一、人话版（一句话）

**JWT Token 就像银行卡：**
- 登录时银行给你发卡（生成 Token）
- 卡上有你的信息和防伪标记（签名）
- 以后买东西刷卡就行（带 Token 请求）
- 商家验证卡是不是真的（Gateway 验证 Token）

---

## 二、JWT Token 是什么？

### JWT 结构

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoi5p2z5bKpIn0.xxxxx
│                                      │                              │
│                                      │                              │
Header（头部）                         Payload（载荷）                Signature（签名）
```

**分解：**

1. **Header（头部）**：
   ```json
   {
     "alg": "HS256",    // 加密算法
     "typ": "JWT"       // 类型
   }
   ```

2. **Payload（载荷）**：
   ```json
   {
     "userId": 1,           // 用户 ID
     "username": "柳岩",    // 用户名
     "iat": 1600000000,     // 签发时间
     "exp": 1600003600      // 过期时间
   }
   ```

3. **Signature（签名）**：
   ```
   HMACSHA256(
     base64(Header) + "." + base64(Payload),
     "secret-key"  // 密钥
   )
   ```

**人话翻译：**
- Header：说明这是什么类型的 Token
- Payload：存放用户信息（谁的卡）
- Signature：防伪标记（防止被篡改）

---

## 三、完整流程图（以 UserService 为例）

### 阶段 1：用户登录（生成 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：用户登录                                               │
│                                                                 │
│  用户在网页输入：                                                │
│    用户名：柳岩                                                  │
│    密码：123456                                                 │
│                                                                 │
│  点击"登录"按钮                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ POST /login
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：请求发送到 Gateway                                     │
│                                                                 │
│  客户端 → Gateway：                                              │
│    POST http://localhost:10010/user/login                       │
│    Body: {"username":"柳岩","password":"123456"}                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：Gateway 转发到 UserService                             │
│                                                                 │
│  Gateway → UserService：                                         │
│    POST http://localhost:8081/login                             │
│    Body: {"username":"柳岩","password":"123456"}                │
│                                                                 │
│  注意：登录接口不需要 Token（因为还没登录）                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：UserService 验证用户名密码                             │
│                                                                 │
│  UserService 收到登录请求：                                      │
│    用户名：柳岩                                                  │
│    密码：123456                                                 │
│                                                                 │
│  查数据库：                                                      │
│    SELECT * FROM user                                           │
│    WHERE username = '柳岩' AND password = '123456'              │
│                                                                 │
│  查到数据：                                                      │
│    id: 1                                                        │
│    username: 柳岩                                               │
│    password: 123456                                             │
│                                                                 │
│  验证结果：✅ 用户名密码正确！                                   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：UserService 生成 JWT Token                             │
│                                                                 │
│  生成 Token 的代码：                                             │
│                                                                 │
│  String token = Jwts.builder()                                  │
│      .setSubject("1")                    // 用户 ID             │
│      .claim("username", "柳岩")          // 用户名              │
│      .setIssuedAt(new Date())            // 签发时间            │
│      .setExpiration(new Date() + 1小时)  // 过期时间            │
│      .signWith(SignatureAlgorithm.HS256, "secret-key")  // 签名 │
│      .compact();                                                │
│                                                                 │
│  生成过程：                                                      │
│                                                                 │
│  1. 准备数据（Payload）：                                       │
│     {                                                           │
│       "sub": "1",              // 用户 ID                       │
│       "username": "柳岩",      // 用户名                        │
│       "iat": 1600000000,       // 签发时间                      │
│       "exp": 1600003600        // 过期时间（1小时后）           │
│     }                                                           │
│                                                                 │
│  2. 准备头部（Header）：                                        │
│     {                                                           │
│       "alg": "HS256",          // 加密算法                      │
│       "typ": "JWT"             // 类型                          │
│     }                                                           │
│                                                                 │
│  3. 生成签名（Signature）：                                     │
│     签名 = HMACSHA256(                                          │
│       base64(Header) + "." + base64(Payload),                  │
│       "secret-key"  // 密钥（开发者自己定义的）                │
│     )                                                           │
│                                                                 │
│  4. 拼接成 Token：                                              │
│     Token = base64(Header) + "." + base64(Payload) + "." + 签名│
│                                                                 │
│  最终生成的 Token：                                             │
│    eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.                        │
│    eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJpYXQiOjE2MDAwMDAw│
│    MDAsImV4cCI6MTYwMDAwMzYwMH0.                                 │
│    xxxxx                                                        │
│                                                                 │
│  就像银行给你发了一张卡！                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回 Token
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：UserService 返回 Token                                 │
│                                                                 │
│  UserService → Gateway：                                         │
│    {                                                            │
│      "code": 200,                                               │
│      "message": "登录成功",                                     │
│      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...."       │
│    }                                                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发响应
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 7 步：Gateway 转发响应给客户端                               │
│                                                                 │
│  Gateway → 客户端：                                              │
│    {                                                            │
│      "code": 200,                                               │
│      "message": "登录成功",                                     │
│      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...."       │
│    }                                                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 8 步：客户端保存 Token                                       │
│                                                                 │
│  客户端收到 Token：                                              │
│    eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9....                     │
│                                                                 │
│  保存到本地：                                                    │
│    localStorage.setItem("token", token);                        │
│                                                                 │
│  就像把银行卡放进钱包！                                          │
└─────────────────────────────────────────────────────────────────┘
```

---

### 阶段 2：访问接口（验证 Token）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：客户端发送请求（带 Token）                             │
│                                                                 │
│  用户点击"查看个人信息"按钮                                      │
│                                                                 │
│  客户端从本地取出 Token：                                        │
│    token = localStorage.getItem("token");                       │
│                                                                 │
│  发送请求：                                                      │
│    GET http://localhost:10010/user/1                            │
│    Headers:                                                     │
│      Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9│
│                                                                 │
│  就像拿着银行卡去买东西！                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 带着 Token 来了
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Gateway 收到请求                                       │
│                                                                 │
│  Gateway 收到：                                                  │
│    路径：/user/1                                                │
│    Headers:                                                     │
│      Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9│
│                                                                 │
│  Gateway 想：先别急着转发，我得先验证 Token                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：Gateway 验证 Token（JwtAuthFilter）                    │
│                                                                 │
│  验证代码：                                                      │
│                                                                 │
│  // 1. 从请求头获取 Token                                       │
│  String authHeader = request.getHeader("Authorization");        │
│  String token = authHeader.replace("Bearer ", "");              │
│                                                                 │
│  // 2. 解析 Token                                               │
│  Claims claims = Jwts.parser()                                  │
│      .setSigningKey("secret-key")  // 用密钥验证签名            │
│      .parseClaimsJws(token)                                     │
│      .getBody();                                                │
│                                                                 │
│  验证过程：                                                      │
│                                                                 │
│  ① 验证签名：                                                   │
│     用密钥重新计算签名：                                         │
│       新签名 = HMACSHA256(Header + Payload, "secret-key")       │
│                                                                 │
│     对比签名：                                                   │
│       新签名 == Token 里的签名？                                │
│       ✅ 相等！说明 Token 没有被篡改                            │
│                                                                 │
│  ② 验证过期时间：                                               │
│     当前时间：1600002000                                        │
│     过期时间：1600003600                                        │
│     当前时间 < 过期时间？                                       │
│     ✅ 是的！Token 还没过期                                     │
│                                                                 │
│  ③ 解析用户信息：                                               │
│     userId = claims.get("sub")        // "1"                    │
│     username = claims.get("username")  // "柳岩"                │
│                                                                 │
│  验证结果：✅ Token 有效！                                       │
│                                                                 │
│  就像商家验证银行卡是不是真的！                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ✅ Token 有效
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：Gateway 添加用户信息到请求头                           │
│                                                                 │
│  Gateway 想：                                                    │
│    Token 验证通过了，我知道是哪个用户了                          │
│    我把用户信息加到请求头，转发给 UserService                    │
│    这样 UserService 就知道是谁发的请求了                         │
│                                                                 │
│  添加请求头：                                                    │
│    ServerHttpRequest newRequest = request.mutate()              │
│        .header("X-User-Id", "1")         // 用户 ID             │
│        .header("X-Username", "柳岩")     // 用户名              │
│        .build();                                                │
│                                                                 │
│  就像快递员在包裹上贴上"已验证"的标签！                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：匹配路由规则                                           │
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
│  第 6 步：查询 Nacos                                             │
│                                                                 │
│  Gateway → Nacos：userservice 在哪？                             │
│  Nacos → Gateway：在 localhost:8081                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 7 步：转发请求到 UserService                                 │
│                                                                 │
│  Gateway → UserService：                                         │
│    GET http://localhost:8081/user/1                             │
│    Headers:                                                     │
│      X-User-Id: 1          ← Gateway 添加的                     │
│      X-Username: 柳岩      ← Gateway 添加的                     │
│                                                                 │
│  注意：                                                          │
│    不需要再带 Token 了                                           │
│    因为 Gateway 已经验证过了                                     │
│    只需要带用户信息                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 8 步：UserService 处理请求                                   │
│                                                                 │
│  UserService 收到：                                              │
│    GET /user/1                                                  │
│    Headers:                                                     │
│      X-User-Id: 1                                               │
│      X-Username: 柳岩                                           │
│                                                                 │
│  UserController 代码：                                           │
│    @GetMapping("/{id}")                                         │
│    public User queryById(                                       │
│        @PathVariable Long id,                                   │
│        @RequestHeader("X-User-Id") Long userId                  │
│    ) {                                                          │
│        // 从请求头获取用户 ID                                   │
│        System.out.println("当前用户：" + userId);               │
│                                                                 │
│        // 查数据库                                              │
│        return userService.queryById(id);                        │
│    }                                                            │
│                                                                 │
│  UserService 想：                                                │
│    请求能到我这里，说明 Gateway 已经验证过 Token 了              │
│    我不需要再验证，直接处理业务逻辑                              │
│                                                                 │
│  查数据库：SELECT * FROM user WHERE id = 1                       │
│  返回数据：{"id":1,"username":"柳岩","address":"湖南"}           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回数据
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 9 步：Gateway 转发响应                                       │
│                                                                 │
│  Gateway → 客户端：{"id":1,"username":"柳岩","address":"湖南"}   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 10 步：客户端收到数据                                        │
│                                                                 │
│  浏览器显示：{"id":1,"username":"柳岩","address":"湖南"}         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、简化版流程图

### 阶段 1：登录（生成 Token）

```
客户端
  │
  │ ① 输入用户名密码
  │ ② POST /user/login
  ↓
Gateway (10010)
  │
  │ ③ 转发登录请求
  ↓
UserService (8081)
  │
  │ ④ 验证用户名密码
  │ ⑤ 生成 JWT Token
  │    - 准备数据：userId=1, username=柳岩
  │    - 用密钥签名：HMACSHA256(..., "secret-key")
  │    - 生成 Token：eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  │ ⑥ 返回 Token
  ↓
Gateway
  │
  │ ⑦ 转发响应
  ↓
客户端
  │
  │ ⑧ 保存 Token 到本地
  │    localStorage.setItem("token", token)
```

### 阶段 2：访问接口（验证 Token）

```
客户端
  │
  │ ① 从本地取出 Token
  │ ② GET /user/1
  │    Headers: Authorization: Bearer <token>
  ↓
Gateway (10010)
  │
  │ ③ 验证 Token
  │    - 验证签名：重新计算签名，对比是否一致
  │    - 验证过期时间：检查是否过期
  │    - 解析用户信息：userId=1, username=柳岩
  │ ④ Token 有效 ✅
  │ ⑤ 添加用户信息到请求头
  │    X-User-Id: 1
  │    X-Username: 柳岩
  │ ⑥ 查询 Nacos：userservice 在 localhost:8081
  │ ⑦ 转发请求
  ↓
UserService (8081)
  │
  │ ⑧ 从请求头获取用户信息
  │ ⑨ 处理业务逻辑（不需要验证 Token）
  │ ⑩ 返回数据
  ↓
Gateway
  │
  │ ⑪ 转发响应
  ↓
客户端
```

---

## 五、核心代码详解

### 1. UserService：生成 Token

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
        .setSubject(user.getId().toString())     // 用户 ID
        .claim("username", user.getUsername())   // 用户名
        .setIssuedAt(new Date())                 // 签发时间
        .setExpiration(new Date(System.currentTimeMillis() + 3600000))  // 过期时间（1小时）
        .signWith(SignatureAlgorithm.HS256, "secret-key")  // 用密钥签名
        .compact();
    
    // 3. 返回 Token
    return Result.success(token);
}
```

**人话翻译：**

```java
// 用户登录
public Result login(...) {
    // 1. 检查用户名密码对不对
    User user = 查数据库(用户名, 密码);
    if (user == null) {
        return "用户名或密码错误";
    }
    
    // 2. 生成 Token（就像银行发卡）
    String token = 生成Token(
        用户ID = user.getId(),
        用户名 = user.getUsername(),
        签发时间 = 现在,
        过期时间 = 1小时后,
        密钥 = "secret-key"  // 用来签名，防止被篡改
    );
    
    // 3. 把 Token 返回给客户端
    return token;
}
```

---

### 2. Gateway：验证 Token

```java
// JWT 鉴权过滤器
@Component
public class JwtAuthFilter implements GlobalFilter {
    
    private static final String SECRET_KEY = "secret-key";  // 密钥（和生成 Token 时一样）
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 1. 获取 Token
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // 没有 Token，拦截
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        String token = authHeader.replace("Bearer ", "");
        
        try {
            // 2. 验证 Token
            Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)  // 用密钥验证签名
                .parseClaimsJws(token)
                .getBody();
            
            // 3. 解析用户信息
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            
            // 4. 添加用户信息到请求头
            ServerHttpRequest newRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-Username", username)
                .build();
            
            // 5. 放行
            return chain.filter(exchange.mutate().request(newRequest).build());
            
        } catch (Exception e) {
            // Token 无效（签名错误或过期），拦截
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
```

**人话翻译：**

```java
// Gateway 的鉴权过滤器
public Mono<Void> filter(...) {
    // 1. 获取 Token
    String token = 从请求头获取("Authorization");
    if (token == null) {
        return "没有 Token，拦截！";
    }
    
    try {
        // 2. 验证 Token（就像商家验证银行卡）
        Claims claims = 验证Token(token, "secret-key");
        
        // 验证过程：
        // ① 用密钥重新计算签名
        // ② 对比签名是否一致（防止被篡改）
        // ③ 检查是否过期
        
        // 3. 解析用户信息
        String userId = claims.get("用户ID");
        String username = claims.get("用户名");
        
        // 4. 把用户信息加到请求头
        添加请求头("X-User-Id", userId);
        添加请求头("X-Username", username);
        
        // 5. 放行
        return 继续往下走();
        
    } catch (Exception e) {
        // Token 无效（签名错误或过期）
        return "Token 无效，拦截！";
    }
}
```

---

### 3. UserService：获取用户信息

```java
// 查询用户接口
@GetMapping("/{id}")
public User queryById(
    @PathVariable Long id,
    @RequestHeader("X-User-Id") Long currentUserId,
    @RequestHeader("X-Username") String currentUsername
) {
    // 从请求头获取当前用户信息（Gateway 添加的）
    System.out.println("当前用户 ID：" + currentUserId);
    System.out.println("当前用户名：" + currentUsername);
    
    // 查数据库
    return userService.queryById(id);
}
```

**人话翻译：**

```java
// 查询用户接口
public User queryById(...) {
    // 从请求头获取用户信息
    // 这是 Gateway 验证 Token 后添加的
    Long userId = 从请求头获取("X-User-Id");
    String username = 从请求头获取("X-Username");
    
    // 我不需要验证 Token
    // 因为 Gateway 已经验证过了
    // 我只需要处理业务逻辑
    
    return 查数据库(id);
}
```

---

## 六、JWT Token 详解

### 1. Token 的三部分

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkifQ.xxxxx
│                                      │                              │
│                                      │                              │
Header（头部）                         Payload（载荷）                Signature（签名）
```

#### Header（头部）

```json
{
  "alg": "HS256",    // 加密算法
  "typ": "JWT"       // 类型
}
```

**Base64 编码后：**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
```

#### Payload（载荷）

```json
{
  "sub": "1",              // 用户 ID
  "username": "柳岩",      // 用户名
  "iat": 1600000000,       // 签发时间
  "exp": 1600003600        // 过期时间
}
```

**Base64 编码后：**
```
eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6MTYwMDAwMzYwMH0
```

#### Signature（签名）

```
HMACSHA256(
  base64(Header) + "." + base64(Payload),
  "secret-key"  // 密钥
)
```

**签名结果：**
```
xxxxx
```

---

### 2. 为什么需要签名？

**场景：黑客想篡改 Token**

```
原始 Token：
  Payload: {"sub":"1","username":"柳岩"}
  
黑客想改成：
  Payload: {"sub":"999","username":"管理员"}
```

**如果没有签名：**
- 黑客可以随意修改 Payload
- 服务器无法发现 Token 被篡改
- 黑客可以冒充任何用户

**有了签名：**
- 黑客修改 Payload 后，签名就对不上了
- 服务器验证签名时会发现不一致
- 拦截请求，黑客无法冒充

**验证过程：**

```
服务器收到 Token：
  Header: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
  Payload: eyJzdWIiOiI5OTkiLCJ1c2VybmFtZSI6Iue7j-eQhuWRmCJ9  ← 被篡改了
  Signature: xxxxx  ← 原来的签名

服务器验证：
  1. 用密钥重新计算签名：
     新签名 = HMACSHA256(Header + Payload, "secret-key")
  
  2. 对比签名：
     新签名 == Token 里的签名？
     ❌ 不一致！说明 Token 被篡改了
  
  3. 拦截请求
```

---

### 3. 密钥的作用

**密钥（Secret Key）：**
- 是一个字符串，开发者自己定义
- 用来生成签名和验证签名
- 必须保密，不能泄露

**示例：**
```java
private static final String SECRET_KEY = "my-secret-key-123456";
```

**生成 Token 时：**
```java
String token = Jwts.builder()
    .setSubject("1")
    .signWith(SignatureAlgorithm.HS256, SECRET_KEY)  // 用密钥签名
    .compact();
```

**验证 Token 时：**
```java
Claims claims = Jwts.parser()
    .setSigningKey(SECRET_KEY)  // 用密钥验证签名
    .parseClaimsJws(token)
    .getBody();
```

**注意：**
- 生成和验证必须用同一个密钥
- 密钥泄露了，黑客就可以伪造 Token
- 实际项目中，密钥应该放在配置文件中，不要写死在代码里

---

## 七、常见问题

### Q1：JWT Token 是加密的吗？

**A：不是！JWT 是编码（Base64），不是加密。**

- **编码**：可以解码，任何人都可以看到内容
- **加密**：需要密钥才能解密

**示例：**

```
Token: eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkifQ

Base64 解码：
  {"sub":"1","username":"柳岩"}
```

任何人都可以解码 Token，看到里面的内容！

**所以：**
- 不要在 Token 里放敏感信息（密码、银行卡号等）
- Token 的安全性靠的是签名，不是加密

---

### Q2：Token 被别人看到了怎么办？

**A：Token 被看到不要紧，关键是不能被篡改。**

- Token 里的内容可以被看到（Base64 解码）
- 但是不能被篡改（有签名保护）
- 黑客看到 Token 可以用它发请求，但不能修改里面的内容

**防止 Token 被盗用：**
- 使用 HTTPS（加密传输）
- 设置过期时间（Token 过期后就不能用了）
- 使用 Refresh Token（定期刷新 Token）

---

### Q3：密钥是怎么来的？

**A：密钥是开发者自己定义的，固定不变。**

```java
// 密钥（开发者自己定义）
private static final String SECRET_KEY = "my-secret-key-123456";
```

**注意：**
- 密钥要足够复杂（防止被猜到）
- 密钥要保密（不能泄露）
- 生成和验证必须用同一个密钥

**实际项目中：**
```yaml
# application.yml
jwt:
  secret-key: my-secret-key-123456  # 密钥放在配置文件
  expire-time: 3600000              # 过期时间（1小时）
```

---

### Q4：Token 过期了怎么办？

**A：重新登录，或者用 Refresh Token 刷新。**

**方案 1：重新登录**
```
Token 过期 → 提示用户重新登录 → 生成新 Token
```

**方案 2：Refresh Token（推荐）**
```
Token 过期 → 用 Refresh Token 换新 Token → 继续使用
```

**Refresh Token 机制：**
- 登录时返回两个 Token：
  - Access Token（短期，1小时）：用来访问接口
  - Refresh Token（长期，7天）：用来刷新 Access Token
- Access Token 过期后，用 Refresh Token 换新的 Access Token
- Refresh Token 过期后，才需要重新登录

---

### Q5：为什么 Gateway 验证 Token 后，UserService 不需要再验证？

**A：因为 Gateway 是统一入口，所有请求都经过 Gateway。**

```
客户端 → Gateway → UserService
         │
         └─ 验证 Token ✅
```

**流程：**
1. 客户端发送请求（带 Token）
2. Gateway 验证 Token
3. Token 有效，Gateway 添加用户信息到请求头
4. Gateway 转发请求到 UserService
5. UserService 从请求头获取用户信息，不需要再验证 Token

**注意：**
- 这种方式要求所有请求都必须经过 Gateway
- 如果直接访问 UserService（绕过 Gateway），就没有鉴权了
- 实际项目中，应该禁止外部直接访问 UserService

---

### Q6：如果黑客直接访问 UserService（绕过 Gateway）怎么办？

**A：应该禁止外部直接访问 UserService。**

**方案 1：网络隔离**
- UserService 部署在内网
- 外部无法直接访问
- 只能通过 Gateway 访问

**方案 2：IP 白名单**
- UserService 只允许 Gateway 的 IP 访问
- 其他 IP 拒绝访问

**方案 3：内部 Token**
- Gateway 转发请求时，添加内部 Token
- UserService 验证内部 Token
- 外部请求没有内部 Token，被拒绝

---

