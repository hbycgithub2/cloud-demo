# JWT 验证 Token 详细过程

## 问题 1：Header + Payload 是从哪里来的？

### 答案：从 Token 里解析出来的！

---

## 完整验证流程

### 第 1 步：收到 Token

```
客户端发来的 Token：
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJleHAiOjE2MDAwMDM2MDB9.xxxxx
│                                      │                                                  │
│                                      │                                                  │
Header（头部）                         Payload（载荷）                                    Signature（签名）
```

---

### 第 2 步：拆分 Token

```java
// Token 是用 "." 分隔的三部分
String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJleHAiOjE2MDAwMDM2MDB9.xxxxx";

// 拆分成三部分
String[] parts = token.split("\\.");

String headerBase64 = parts[0];     // eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
String payloadBase64 = parts[1];    // eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJleHAiOjE2MDAwMDM2MDB9
String signatureBase64 = parts[2];  // xxxxx
```

**人话：**
- Token 就像一个包裹，用 "." 分成三段
- 第 1 段：Header（头部）
- 第 2 段：Payload（载荷）
- 第 3 段：Signature（签名）

---

### 第 3 步：解码 Header 和 Payload

```java
// Base64 解码 Header
String headerJson = Base64.decode(headerBase64);
// 结果：{"alg":"HS256","typ":"JWT"}

// Base64 解码 Payload
String payloadJson = Base64.decode(payloadBase64);
// 结果：{"sub":"1","username":"柳岩","exp":1600003600}
```

**人话：**
- Header 和 Payload 是用 Base64 编码的
- 解码后就能看到里面的内容
- 任何人都可以解码（不是加密）

---

### 第 4 步：验证签名

```java
// 1. 用密钥重新计算签名
String newSignature = HMACSHA256(
    headerBase64 + "." + payloadBase64,  // ← Header 和 Payload 从这里来！
    "secret-key"
);

// 2. 对比签名
if (newSignature.equals(signatureBase64)) {
    // 签名一致，Token 没有被篡改 ✅
} else {
    // 签名不一致，Token 被篡改了 ❌
}
```

**人话：**
- 用 Token 里的 Header 和 Payload（已经解码了）
- 加上密钥，重新计算签名
- 对比新签名和 Token 里的签名
- 一致就说明 Token 没有被篡改

---

### 第 5 步：验证过期时间

```java
// 从 Payload 里获取过期时间
long exp = payloadJson.get("exp");  // 1600003600

// 获取当前时间
long now = System.currentTimeMillis() / 1000;  // 1600002000

// 对比时间
if (now < exp) {
    // 当前时间 < 过期时间，Token 还没过期 ✅
} else {
    // 当前时间 >= 过期时间，Token 过期了 ❌
}
```

**人话：**
- 从 Payload 里取出过期时间（exp）
- 获取当前时间
- 对比：当前时间 < 过期时间？
- 是的话，Token 还有效

---

## 完整代码示例

### Jwts.parser() 内部做了什么

```java
// 你写的代码
Claims claims = Jwts.parser()
    .setSigningKey("secret-key")
    .parseClaimsJws(token)
    .getBody();

// Jwts.parser() 内部做的事情：
public Claims parseClaimsJws(String token) {
    // 1. 拆分 Token
    String[] parts = token.split("\\.");
    String headerBase64 = parts[0];
    String payloadBase64 = parts[1];
    String signatureBase64 = parts[2];
    
    // 2. 解码 Header 和 Payload
    String headerJson = Base64.decode(headerBase64);
    String payloadJson = Base64.decode(payloadBase64);
    
    // 3. 验证签名
    String newSignature = HMACSHA256(
        headerBase64 + "." + payloadBase64,  // ← 从这里来！
        "secret-key"
    );
    
    if (!newSignature.equals(signatureBase64)) {
        throw new SignatureException("签名验证失败");
    }
    
    // 4. 解析 Payload
    Claims claims = JSON.parse(payloadJson);
    
    // 5. 验证过期时间
    long exp = claims.get("exp");
    long now = System.currentTimeMillis() / 1000;
    
    if (now >= exp) {
        throw new ExpiredJwtException("Token 已过期");
    }
    
    // 6. 返回 Claims
    return claims;
}
```

---

## 图解验证过程

```
┌─────────────────────────────────────────────────────────────────┐
│  收到 Token                                                      │
│                                                                 │
│  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.                          │
│  eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJleHAiOjE2MDAwMDM2MDB9.│
│  xxxxx                                                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：拆分 Token（用 "." 分隔）                              │
│                                                                 │
│  parts[0] = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9  ← Header     │
│  parts[1] = eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJleHAiOjE2│
│             MDAwMDM2MDB9  ← Payload                             │
│  parts[2] = xxxxx  ← Signature                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Base64 解码 Header 和 Payload                          │
│                                                                 │
│  Header（解码后）：                                             │
│    {"alg":"HS256","typ":"JWT"}                                  │
│                                                                 │
│  Payload（解码后）：                                            │
│    {"sub":"1","username":"柳岩","exp":1600003600}               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：验证签名                                               │
│                                                                 │
│  用 Token 里的 Header 和 Payload 重新计算签名：                  │
│                                                                 │
│  新签名 = HMACSHA256(                                           │
│    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +  ← Header（编码后） │
│    "." +                                                        │
│    "eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJleHAiOjE2MDAwMDM2│
│    MDB9",  ← Payload（编码后）                                  │
│    "secret-key"  ← 密钥                                         │
│  )                                                              │
│                                                                 │
│  对比签名：                                                      │
│    新签名 = xxxxx                                               │
│    Token 里的签名 = xxxxx                                       │
│    xxxxx == xxxxx？✅ 相等！                                    │
│                                                                 │
│  结论：Token 没有被篡改 ✅                                       │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：验证过期时间                                           │
│                                                                 │
│  从 Payload 里获取过期时间：                                     │
│    exp = 1600003600  （2020-09-13 18:00:00）                   │
│                                                                 │
│  获取当前时间：                                                  │
│    now = 1600002000  （2020-09-13 17:33:20）                   │
│                                                                 │
│  对比时间：                                                      │
│    1600002000 < 1600003600？                                    │
│    ✅ 是的！当前时间 < 过期时间                                 │
│                                                                 │
│  结论：Token 还没过期 ✅                                         │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：解析用户信息                                           │
│                                                                 │
│  从 Payload 里获取用户信息：                                     │
│    userId = claims.get("sub")        // "1"                     │
│    username = claims.get("username")  // "柳岩"                 │
│                                                                 │
│  返回 Claims 对象                                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 问题 1 详解：Header + Payload 从哪里来？

### 答案：从 Token 里拆分出来的！

```
Token 结构：
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJleHAiOjE2MDAwMDM2MDB9.xxxxx
│                                      │                                                  │
│                                      │                                                  │
parts[0]                               parts[1]                                           parts[2]
Header（编码后）                       Payload（编码后）                                  Signature
```

**步骤：**

1. **拆分 Token**：
   ```java
   String[] parts = token.split("\\.");
   String headerBase64 = parts[0];   // Header（编码后）
   String payloadBase64 = parts[1];  // Payload（编码后）
   String signatureBase64 = parts[2]; // Signature
   ```

2. **用这两部分重新计算签名**：
   ```java
   String newSignature = HMACSHA256(
       headerBase64 + "." + payloadBase64,  // ← 从 Token 里拆分出来的！
       "secret-key"
   );
   ```

3. **对比签名**：
   ```java
   if (newSignature.equals(signatureBase64)) {
       // 签名一致 ✅
   }
   ```

**人话：**
- Token 就像一个包裹：`Header.Payload.Signature`
- 拆开包裹，取出 Header 和 Payload
- 用 Header + Payload + 密钥，重新计算签名
- 对比新签名和包裹里的签名
- 一致就说明包裹没有被打开过（没有被篡改）

---

## 问题 2 详解：怎么验证过期时间？

### 答案：从 Payload 里取出过期时间，和当前时间对比！

```
Payload（解码后）：
{
  "sub": "1",
  "username": "柳岩",
  "exp": 1600003600  ← 过期时间（时间戳）
}
```

**步骤：**

1. **从 Payload 里取出过期时间**：
   ```java
   long exp = claims.get("exp");  // 1600003600
   ```

2. **获取当前时间**：
   ```java
   long now = System.currentTimeMillis() / 1000;  // 1600002000
   ```

3. **对比时间**：
   ```java
   if (now < exp) {
       // 当前时间 < 过期时间，Token 还没过期 ✅
   } else {
       // 当前时间 >= 过期时间，Token 过期了 ❌
       throw new ExpiredJwtException("Token 已过期");
   }
   ```

**时间戳说明：**

```
exp = 1600003600
  ↓ 转换成日期
2020-09-13 18:00:00

now = 1600002000
  ↓ 转换成日期
2020-09-13 17:33:20

对比：
  17:33:20 < 18:00:00？
  ✅ 是的！Token 还没过期
```

**人话：**
- Token 里有一个过期时间（exp）
- 就像牛奶的保质期
- 检查：现在的时间 < 保质期？
- 是的话，Token 还能用
- 不是的话，Token 过期了

---

## 完整示例

### 生成 Token

```java
// UserService 生成 Token
String token = Jwts.builder()
    .setSubject("1")                                    // 用户 ID
    .claim("username", "柳岩")                          // 用户名
    .setIssuedAt(new Date())                            // 签发时间：2020-09-13 17:00:00
    .setExpiration(new Date(System.currentTimeMillis() + 3600000))  // 过期时间：2020-09-13 18:00:00（1小时后）
    .signWith(SignatureAlgorithm.HS256, "secret-key")  // 用密钥签名
    .compact();

// 生成的 Token：
// eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiLmn7PlsqkiLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6MTYwMDAwMzYwMH0.xxxxx
```

---

### 验证 Token（30 分钟后）

```java
// 当前时间：2020-09-13 17:30:00
// Token 过期时间：2020-09-13 18:00:00

Claims claims = Jwts.parser()
    .setSigningKey("secret-key")
    .parseClaimsJws(token)
    .getBody();

// 内部验证过程：
// 1. 拆分 Token：Header.Payload.Signature
// 2. 解码 Header 和 Payload
// 3. 验证签名：重新计算签名，对比是否一致 ✅
// 4. 验证过期时间：17:30:00 < 18:00:00？✅
// 5. 返回 Claims

// 结果：✅ Token 有效！
```

---

### 验证 Token（2 小时后）

```java
// 当前时间：2020-09-13 19:00:00
// Token 过期时间：2020-09-13 18:00:00

Claims claims = Jwts.parser()
    .setSigningKey("secret-key")
    .parseClaimsJws(token)
    .getBody();

// 内部验证过程：
// 1. 拆分 Token：Header.Payload.Signature
// 2. 解码 Header 和 Payload
// 3. 验证签名：重新计算签名，对比是否一致 ✅
// 4. 验证过期时间：19:00:00 < 18:00:00？❌
// 5. 抛出异常：ExpiredJwtException

// 结果：❌ Token 过期了！
```

---

## 总结

### 问题 1：Header + Payload 从哪里来？

**答案：从 Token 里拆分出来的！**

```
Token = Header.Payload.Signature
         │      │
         │      └─ 拆分出来
         └─ 拆分出来

用这两部分重新计算签名，对比是否一致
```

---

### 问题 2：怎么验证过期时间？

**答案：从 Payload 里取出过期时间，和当前时间对比！**

```
Payload = {"sub":"1","username":"柳岩","exp":1600003600}
                                          │
                                          └─ 过期时间

当前时间 < 过期时间？
  ✅ 是 → Token 还没过期
  ❌ 否 → Token 过期了
```

---

## 一句话总结

**验证 Token 就像验证包裹：**
1. 拆开包裹（拆分 Token）
2. 取出内容（Header + Payload）
3. 重新封装（重新计算签名）
4. 对比封条（对比签名）
5. 检查保质期（验证过期时间）

**Header 和 Payload 就是从 Token 里拆分出来的，过期时间就是从 Payload 里取出来和当前时间对比的！**
