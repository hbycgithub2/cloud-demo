# JWT 生成和验证 - 超简单版

## 一、生成 Token（登录）

### 人话版

```
你去银行办卡：
1. 你：我要办卡（输入账号密码）
2. 银行：验证你的身份证
3. 银行：给你一张银行卡（Token）
4. 你：拿着卡回家
```

### 流程图

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：用户登录                                               │
│                                                                 │
│  浏览器输入：                                                    │
│    账号：admin                                                   │
│    密码：123456                                                  │
│    点击"登录"                                                    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ POST /user/login
                            │ {"username":"admin","password":"123456"}
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：UserService 验证                                       │
│                                                                 │
│  查数据库：有这个用户吗？                                        │
│    ✅ 有，用户名是 admin                                         │
│                                                                 │
│  密码对吗？                                                      │
│    ✅ 对，密码是 123456                                          │
│                                                                 │
│  验证通过！                                                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：生成 Token                                             │
│                                                                 │
│  准备用户信息：                                                  │
│    userId = 1                                                   │
│    username = admin                                             │
│                                                                 │
│  调用 JWT 库：                                                   │
│    Token = JWT.生成(用户信息, 密钥)                              │
│                                                                 │
│  生成的 Token：                                                  │
│    eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.                        │
│    eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ.                  │
│    SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c                 │
│                                                                 │
│  Token 里包含：                                                  │
│    - userId = 1                                                 │
│    - username = admin                                           │
│    - 过期时间 = 2小时后                                          │
│    - 签名（防伪标记）                                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回 Token
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：浏览器保存 Token                                       │
│                                                                 │
│  localStorage.setItem('token', 'eyJhbGciOiJIUzI1NiIsInR5cCI6...')│
│                                                                 │
│  就像把银行卡放进钱包                                            │
└─────────────────────────────────────────────────────────────────┘
```

### 代码版（简化）

```java
// 用户登录
@PostMapping("/login")
public Map<String, Object> login(String username, String password) {
    // 1. 验证用户名密码
    if ("admin".equals(username) && "123456".equals(password)) {
        
        // 2. 生成 Token
        String token = Jwts.builder()
            .claim("userId", 1)           // 放入用户ID
            .claim("username", "admin")   // 放入用户名
            .setExpiration(2小时后)        // 设置过期时间
            .signWith(密钥)                // 用密钥签名（防伪）
            .compact();                   // 生成 Token
        
        // 3. 返回 Token
        return {"token": token};
    }
}
```

---

## 二、验证 Token（访问接口）

### 人话版

```
你去银行取钱：
1. 你：拿出银行卡（Token）
2. 银行：刷卡验证
   - 这张卡是真的吗？（验证签名）
   - 这张卡过期了吗？（检查过期时间）
   - 这张卡是谁的？（读取用户信息）
3. 银行：验证通过，给你钱
```

### 流程图

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：用户访问个人信息                                        │
│                                                                 │
│  浏览器：                                                        │
│    从 localStorage 取出 Token                                    │
│    token = localStorage.getItem('token')                        │
│                                                                 │
│    发送请求：                                                    │
│    GET /user/info                                               │
│    Headers: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI...│
│                                                                 │
│  就像拿出银行卡                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 带着 Token 的请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：UserService 收到 Token                                 │
│                                                                 │
│  从请求头取出 Token：                                            │
│    token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：验证 Token                                             │
│                                                                 │
│  调用 JWT 库：                                                   │
│    用户信息 = JWT.验证(Token, 密钥)                              │
│                                                                 │
│  JWT 库内部做了什么？                                            │
│                                                                 │
│  3.1 拆分 Token                                                 │
│      Token = 第1部分.第2部分.第3部分                             │
│                                                                 │
│  3.2 验证签名（检查是否被改过）                                  │
│      用密钥重新计算签名                                          │
│      新签名 == 原签名？                                          │
│        ✅ 是 → Token 是真的                                      │
│        ❌ 否 → Token 是假的，拒绝！                              │
│                                                                 │
│  3.3 检查过期时间                                               │
│      现在时间 < 过期时间？                                       │
│        ✅ 是 → Token 还没过期                                    │
│        ❌ 否 → Token 过期了，拒绝！                              │
│                                                                 │
│  3.4 读取用户信息                                               │
│      userId = 1                                                 │
│      username = admin                                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 验证通过
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：查询用户详细信息                                        │
│                                                                 │
│  根据 userId 查数据库：                                          │
│    SELECT * FROM user WHERE id = 1                              │
│                                                                 │
│  查到用户信息：                                                  │
│    id: 1                                                        │
│    username: 柳岩                                                │
│    address: 湖南省衡阳市                                         │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回用户信息
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：浏览器显示用户信息                                      │
│                                                                 │
│  收到数据：                                                      │
│    {                                                            │
│      "id": 1,                                                   │
│      "username": "柳岩",                                         │
│      "address": "湖南省衡阳市"                                   │
│    }                                                            │
│                                                                 │
│  显示在页面上                                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 代码版（简化）

```java
// 获取用户信息
@GetMapping("/info")
public Map<String, Object> getUserInfo(@RequestHeader("Authorization") String auth) {
    // 1. 取出 Token
    String token = auth.replace("Bearer ", "");
    
    // 2. 验证 Token
    Claims claims = Jwts.parser()
        .setSigningKey(密钥)      // 用密钥验证
        .parseClaimsJws(token)   // 解析 Token
        .getBody();              // 获取用户信息
    
    // 3. 提取用户信息
    Integer userId = claims.get("userId");      // 1
    String username = claims.get("username");   // admin
    
    // 4. 查询用户详细信息
    User user = userService.queryById(userId);
    
    // 5. 返回
    return {"data": user};
}
```

---

## 三、完整流程对比

### 登录流程（生成 Token）

```
用户                    UserService
  │                         │
  │  1. 输入账号密码         │
  │  admin / 123456         │
  │─────────────────────────>│
  │                         │
  │                         │  2. 验证账号密码
  │                         │     查数据库 ✅
  │                         │
  │                         │  3. 生成 Token
  │                         │     userId=1
  │                         │     username=admin
  │                         │     + 密钥签名
  │                         │     = Token
  │                         │
  │  4. 返回 Token          │
  │<─────────────────────────│
  │  eyJhbGciOiJIUzI1NiIsInR5cCI6...
  │                         │
  │  5. 保存 Token          │
  │  localStorage.setItem() │
  │                         │
```

### 访问流程（验证 Token）

```
用户                    UserService
  │                         │
  │  1. 取出 Token          │
  │  localStorage.getItem() │
  │                         │
  │  2. 带 Token 访问       │
  │  GET /user/info         │
  │  Authorization: Bearer Token
  │─────────────────────────>│
  │                         │
  │                         │  3. 验证 Token
  │                         │     用密钥重新签名
  │                         │     签名一致 ✅
  │                         │     没过期 ✅
  │                         │
  │                         │  4. 提取用户信息
  │                         │     userId = 1
  │                         │
  │                         │  5. 查数据库
  │                         │     SELECT * FROM user
  │                         │     WHERE id = 1
  │                         │
  │  6. 返回用户信息        │
  │<─────────────────────────│
  │  {"id":1,"username":"柳岩"}
  │                         │
```

---

## 四、关键点（人话版）

### 1. Token 是什么？

```
Token = 银行卡
  - 卡上有你的信息（userId, username）
  - 卡有防伪标记（签名）
  - 卡有有效期（过期时间）
```

### 2. 为什么安全？

```
签名 = 防伪标记

黑客想改 Token：
  原来：userId=1, 签名=ABC
  改成：userId=999, 签名=ABC（还是原来的签名）

服务器验证：
  用密钥重新计算签名 = XYZ
  对比：ABC ≠ XYZ
  结论：签名对不上，Token 被改过了！拒绝！

为什么黑客算不出正确的签名？
  因为黑客不知道密钥！
```

### 3. 生成 Token 的过程

```
1. 准备用户信息：userId=1, username=admin
2. 用密钥签名：签名 = 加密(用户信息, 密钥)
3. 拼接：Token = 用户信息 + 签名
```

### 4. 验证 Token 的过程

```
1. 拆分 Token：用户信息 + 签名
2. 用密钥重新签名：新签名 = 加密(用户信息, 密钥)
3. 对比：新签名 == 原签名？
   - 是 → Token 是真的 ✅
   - 否 → Token 是假的 ❌
4. 检查过期时间
5. 提取用户信息
```

---

## 五、总结（一句话）

**生成 Token：** 用户登录 → 验证成功 → 把用户信息+密钥签名 → 生成 Token → 返回给用户

**验证 Token：** 用户带 Token 访问 → 用密钥重新签名 → 对比签名是否一致 → 检查是否过期 → 提取用户信息 → 处理请求

**就像：** 登录 = 办银行卡，访问 = 刷卡，签名 = 防伪标记，密钥 = 只有银行知道的秘密。
