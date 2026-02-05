# Token 应该放什么数据 - 性能优化

## 一、核心问题

**问题 1：Token 一般都带什么数据？**

**答案：只放必要的基本信息！**

**问题 2：数据带多了会不会影响性能？**

**答案：会！Token 越大，性能越差！**

---

## 二、Token 应该放什么数据

### 推荐方案：只放必要信息 ✅

```json
{
  "sub": "1",                    // 用户 ID（必须）
  "username": "张三",            // 用户名（可选）
  "iat": 1600000000,             // 签发时间（必须）
  "exp": 1600003600              // 过期时间（必须）
}
```

**大小：约 150 字节**

**包含：**
- ✅ 用户 ID（必须）：用来查询用户数据
- ✅ 用户名（可选）：方便显示
- ✅ 签发时间（必须）：记录 Token 生成时间
- ✅ 过期时间（必须）：防止 Token 永久有效

---

### 错误方案：放太多数据 ❌

```json
{
  "sub": "1",
  "username": "张三",
  "email": "zhangsan@example.com",
  "phone": "13800138000",
  "address": "北京市朝阳区xxx街道xxx号",
  "age": 25,
  "gender": "男",
  "avatar": "https://example.com/avatar/zhangsan.jpg",
  "role": "user",
  "permissions": ["user:read", "user:write", "order:read", "order:write", "product:read"],
  "department": "技术部",
  "position": "工程师",
  "salary": 20000,
  "idCard": "110101199001011234",
  "bankCard": "6222021234567890123",
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：约 800 字节**

**问题：**
- ❌ Token 太大（800 字节）
- ❌ 每次请求都要传输 800 字节
- ❌ 性能差
- ❌ 安全风险（敏感信息泄露）

---

## 三、Token 大小对性能的影响

### 场景：1000 万次请求

#### 方案 1：Token 150 字节（推荐）✅

```
单次请求：
  Token 大小：150 字节
  传输时间：0.001ms（忽略不计）

1000 万次请求：
  总传输量：150 字节 × 1000 万 = 1.5 GB
  总传输时间：10 秒
```

---

#### 方案 2：Token 800 字节（不推荐）❌

```
单次请求：
  Token 大小：800 字节
  传输时间：0.005ms

1000 万次请求：
  总传输量：800 字节 × 1000 万 = 8 GB
  总传输时间：50 秒

对比：
  传输量增加：8 GB - 1.5 GB = 6.5 GB（多了 4.3 倍）
  传输时间增加：50 秒 - 10 秒 = 40 秒（多了 4 倍）
```

---

### 性能影响

```
Token 大小对性能的影响：

1. 网络传输
   - Token 越大，传输时间越长
   - 移动网络影响更大

2. 带宽消耗
   - Token 越大，带宽消耗越大
   - 成本增加

3. 解析时间
   - Token 越大，解析时间越长
   - CPU 消耗增加

4. 存储空间
   - 客户端存储 Token
   - Token 越大，占用空间越大
```

---

## 四、不同场景的 Token 设计

### 场景 1：普通用户（推荐）✅

```json
{
  "sub": "1",           // 用户 ID
  "username": "张三",   // 用户名
  "iat": 1600000000,    // 签发时间
  "exp": 1600003600     // 过期时间
}
```

**大小：150 字节**

**说明：**
- 只放用户 ID 和用户名
- 其他信息从数据库查询
- 性能最好

---

### 场景 2：需要角色信息

```json
{
  "sub": "1",
  "username": "张三",
  "role": "admin",      // 角色
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：180 字节**

**说明：**
- 加了角色信息
- 方便权限判断
- 大小增加不多

---

### 场景 3：需要多个角色

```json
{
  "sub": "1",
  "username": "张三",
  "roles": ["admin", "user", "manager"],  // 多个角色
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：220 字节**

**说明：**
- 支持多个角色
- 大小还可以接受

---

### 场景 4：需要权限列表（不推荐）❌

```json
{
  "sub": "1",
  "username": "张三",
  "permissions": [
    "user:read", "user:write", "user:delete",
    "order:read", "order:write", "order:delete",
    "product:read", "product:write", "product:delete",
    "payment:read", "payment:write"
  ],
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：500 字节**

**问题：**
- ❌ Token 太大
- ❌ 权限变了，Token 要重新生成
- ❌ 不推荐

**推荐方案：**
- 权限信息存 Redis
- Token 只放用户 ID
- 需要权限时查 Redis

---

## 五、Token 大小优化技巧

### 技巧 1：使用短字段名

#### 优化前（不推荐）❌

```json
{
  "userId": "1",
  "username": "张三",
  "issuedAt": 1600000000,
  "expirationTime": 1600003600
}
```

**大小：180 字节**

---

#### 优化后（推荐）✅

```json
{
  "sub": "1",      // subject（主题）= userId
  "name": "张三",  // name = username
  "iat": 1600000000,  // issued at（签发时间）
  "exp": 1600003600   // expiration（过期时间）
}
```

**大小：150 字节**

**节省：30 字节（17%）**

---

### 技巧 2：不放不必要的信息

#### 优化前（不推荐）❌

```json
{
  "sub": "1",
  "username": "张三",
  "email": "zhangsan@example.com",     // 不必要
  "phone": "13800138000",              // 不必要
  "avatar": "https://example.com/...", // 不必要
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：350 字节**

---

#### 优化后（推荐）✅

```json
{
  "sub": "1",
  "username": "张三",
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：150 字节**

**节省：200 字节（57%）**

**说明：**
- 邮箱、电话、头像等信息从数据库查询
- Token 只放必要信息

---

### 技巧 3：使用数字代替字符串

#### 优化前（不推荐）❌

```json
{
  "sub": "1",
  "username": "张三",
  "role": "administrator",  // 字符串
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：200 字节**

---

#### 优化后（推荐）✅

```json
{
  "sub": "1",
  "username": "张三",
  "role": 1,  // 数字（1=admin, 2=user, 3=guest）
  "iat": 1600000000,
  "exp": 1600003600
}
```

**大小：160 字节**

**节省：40 字节（20%）**

---

## 六、实际案例对比

### 案例：电商系统（1000 万用户在线）

#### 方案 1：Token 150 字节 ✅

```
Token 内容：
  {
    "sub": "1",
    "username": "张三",
    "iat": 1600000000,
    "exp": 1600003600
  }

每秒请求：10 万次
每秒传输：150 字节 × 10 万 = 15 MB
每天传输：15 MB × 86400 秒 = 1.3 TB

带宽成本：
  1.3 TB/天 × 30 天 = 39 TB/月
  39 TB × 1 元/GB = 39000 元/月
```

---

#### 方案 2：Token 800 字节 ❌

```
Token 内容：
  {
    "sub": "1",
    "username": "张三",
    "email": "zhangsan@example.com",
    "phone": "13800138000",
    "address": "北京市朝阳区...",
    "permissions": [...],
    ...
  }

每秒请求：10 万次
每秒传输：800 字节 × 10 万 = 80 MB
每天传输：80 MB × 86400 秒 = 6.9 TB

带宽成本：
  6.9 TB/天 × 30 天 = 207 TB/月
  207 TB × 1 元/GB = 207000 元/月

对比：
  多花费：207000 - 39000 = 168000 元/月
  多花费：168000 × 12 = 2016000 元/年（200 万/年）
```

**结论：Token 太大，每年多花 200 万！**

---

## 七、推荐方案

### 方案：Token + Redis（混合）✅

```
Token 里放：
  - 用户 ID（必须）
  - 用户名（可选）
  - 签发时间（必须）
  - 过期时间（必须）

Redis 里放：
  - 权限列表
  - 角色信息
  - 其他扩展信息
```

#### 代码示例

```java
// 生成 Token
@PostMapping("/login")
public Result login(@RequestBody LoginRequest request) {
    User user = userService.login(request.getUsername(), request.getPassword());
    
    // 1. 生成 Token（只放基本信息）
    String token = Jwts.builder()
        .setSubject(user.getId().toString())  // 用户 ID
        .claim("username", user.getUsername())  // 用户名
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 3600000))
        .signWith(SignatureAlgorithm.HS256, "secret-key")
        .compact();
    
    // 2. 把权限信息存 Redis
    redisTemplate.opsForValue().set(
        "user:permissions:" + user.getId(),
        user.getPermissions(),
        1,
        TimeUnit.HOURS
    );
    
    return Result.success(token);
}

// 验证权限
@DeleteMapping("/user/{id}")
public Result deleteUser(
    @RequestHeader("Authorization") String authHeader,
    @PathVariable Long id
) {
    // 1. 从 Token 解析用户 ID（快，不查 Redis）
    String token = authHeader.replace("Bearer ", "");
    Claims claims = Jwts.parser()
        .setSigningKey("secret-key")
        .parseClaimsJws(token)
        .getBody();
    
    Long userId = Long.parseLong(claims.getSubject());
    
    // 2. 从 Redis 获取权限（只有需要权限时才查）
    List<String> permissions = redisTemplate.opsForValue()
        .get("user:permissions:" + userId);
    
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

## 八、总结

### Token 应该放什么数据

```
必须放：
  ✅ 用户 ID（sub）
  ✅ 签发时间（iat）
  ✅ 过期时间（exp）

可选放：
  ⚠️ 用户名（username）
  ⚠️ 角色（role）

不要放：
  ❌ 邮箱、电话、地址
  ❌ 权限列表（太长）
  ❌ 敏感信息（密码、银行卡）
  ❌ 不常用的信息
```

### Token 大小对性能的影响

```
Token 150 字节（推荐）：
  - 1000 万次请求 = 1.5 GB
  - 带宽成本：39000 元/月

Token 800 字节（不推荐）：
  - 1000 万次请求 = 8 GB
  - 带宽成本：207000 元/月
  - 多花费：168000 元/月（200 万/年）

结论：Token 越大，成本越高！
```

### 优化技巧

```
1. 使用短字段名
   - userId → sub
   - username → name
   - 节省 17%

2. 不放不必要的信息
   - 只放用户 ID 和用户名
   - 其他信息从数据库查询
   - 节省 57%

3. 使用数字代替字符串
   - "administrator" → 1
   - 节省 20%

4. 使用混合方案
   - 基本信息存 Token
   - 扩展信息存 Redis
   - 性能最好
```

### 推荐方案

```
Token + Redis（混合方案）：
  - Token 只放用户 ID、用户名（150 字节）
  - 权限信息存 Redis
  - 大部分请求只需要解析 Token（快）
  - 需要权限时才查 Redis
  - 性能好，成本低
```

### 一句话总结

**Token 应该只放必要的基本信息（用户 ID、用户名、过期时间），大小控制在 150-200 字节。Token 越大，性能越差，成本越高（每年多花 200 万）。推荐混合方案：基本信息存 Token，扩展信息存 Redis，性能最好，成本最低！**
