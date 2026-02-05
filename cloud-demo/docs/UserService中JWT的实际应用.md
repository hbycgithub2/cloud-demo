# UserService 中 JWT 的实际应用

## 一、需要添加的代码

### 1.1 添加 JWT 依赖

首先在 `user-service/pom.xml` 中添加 JWT 依赖：

```xml
<!-- JWT 依赖 -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.1</version>
</dependency>
```

### 1.2 创建 JWT 工具类

创建文件：`user-service/src/main/java/cn/itcast/user/util/JwtUtil.java`

```java
package cn.itcast.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;

public class JwtUtil {
    
    // 密钥（实际项目中应该放在配置文件中）
    private static final String SECRET_KEY = "my-secret-key-123456-userservice";
    
    // Token 有效期：2小时
    private static final long EXPIRATION_TIME = 2 * 60 * 60 * 1000;
    
    /**
     * 生成 Token
     * @param userId 用户ID
     * @param username 用户名
     * @return Token字符串
     */
    public static String generateToken(Long userId, String username) {
        // 当前时间
        Date now = new Date();
        // 过期时间 = 当前时间 + 2小时
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);
        
        // 生成 Token
        String token = Jwts.builder()
            // 设置 Header（自动生成）
            .setHeaderParam("alg", "HS256")
            .setHeaderParam("typ", "JWT")
            
            // 设置 Payload（用户数据）
            .claim("userId", userId)           // 用户ID
            .claim("username", username)       // 用户名
            .setIssuedAt(now)                  // 签发时间
            .setExpiration(expiration)         // 过期时间
            
            // 设置 Signature（签名）
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
            
            // 生成最终的 Token
            .compact();
        
        return token;
    }
    
    /**
     * 验证并解析 Token
     * @param token Token字符串
     * @return Claims（包含用户信息）
     */
    public static Claims parseToken(String token) {
        try {
            // 解析 Token
            Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)     // 用密钥验证签名
                .parseClaimsJws(token)         // 解析 Token
                .getBody();                    // 获取 Payload
            
            return claims;
            
        } catch (Exception e) {
            // Token 无效或过期
            throw new RuntimeException("Token 无效: " + e.getMessage());
        }
    }
    
    /**
     * 从 Token 中获取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }
    
    /**
     * 从 Token 中获取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }
}
```

### 1.3 添加登录接口

修改 `UserController.java`，添加登录接口：

```java
package cn.itcast.user.web;

import cn.itcast.user.config.PatternProperties;
import cn.itcast.user.pojo.User;
import cn.itcast.user.service.UserService;
import cn.itcast.user.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PatternProperties properties;

    /**
     * 登录接口 - 生成 Token
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");
        
        // 简化版：直接验证（实际项目中应该查数据库）
        if ("admin".equals(username) && "123456".equals(password)) {
            // 验证成功，生成 Token
            String token = JwtUtil.generateToken(1L, username);
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "登录成功");
            result.put("token", token);
            
            log.info("用户 {} 登录成功，生成 Token: {}", username, token);
            
            return result;
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 401);
            result.put("message", "用户名或密码错误");
            return result;
        }
    }
    
    /**
     * 获取用户信息 - 验证 Token
     */
    @GetMapping("/info")
    public Map<String, Object> getUserInfo(@RequestHeader("Authorization") String authorization) {
        try {
            // 1. 从请求头获取 Token
            // Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
            String token = authorization.replace("Bearer ", "");
            
            // 2. 验证并解析 Token
            Long userId = JwtUtil.getUserId(token);
            String username = JwtUtil.getUsername(token);
            
            log.info("Token 验证成功，userId: {}, username: {}", userId, username);
            
            // 3. 根据 userId 查询用户信息
            User user = userService.queryById(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("message", "获取成功");
            result.put("data", user);
            
            return result;
            
        } catch (Exception e) {
            log.error("Token 验证失败: {}", e.getMessage());
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 401);
            result.put("message", "Token 无效");
            return result;
        }
    }

    @GetMapping("prop")
    public PatternProperties properties(){
        return properties;
    }

    @GetMapping("now")
    public String now(){
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(properties.getDateformat()));
    }

    @GetMapping("/{id}")
    public User queryById(@PathVariable("id") Long id,
                          @RequestHeader(value = "Truth",required = false) String truth) {
        System.out.println("truth: "+truth);
        return userService.queryById(id);
    }
}
```

## 二、JWT 生成 Token 的详细过程

### 2.1 用户登录（生成 Token）

```java
// 用户登录请求
POST http://localhost:8081/user/login
Body: {
    "username": "admin",
    "password": "123456"
}

// UserController.login() 方法执行：

// 第 1 步：验证用户名密码
if ("admin".equals(username) && "123456".equals(password)) {
    // 验证成功
}

// 第 2 步：调用 JwtUtil.generateToken() 生成 Token
String token = JwtUtil.generateToken(1L, "admin");

// JwtUtil.generateToken() 内部执行：

// 2.1 准备数据
Date now = new Date();                              // 当前时间：2026-01-23 10:00:00
Date expiration = new Date(now.getTime() + 7200000); // 过期时间：2026-01-23 12:00:00

// 2.2 构建 Token
String token = Jwts.builder()
    // Header（自动生成）
    .setHeaderParam("alg", "HS256")
    .setHeaderParam("typ", "JWT")
    // 结果：{"alg":"HS256","typ":"JWT"}
    
    // Payload（用户数据）
    .claim("userId", 1L)
    .claim("username", "admin")
    .setIssuedAt(now)
    .setExpiration(expiration)
    // 结果：{"userId":1,"username":"admin","iat":1706000000,"exp":1706007200}
    
    // Signature（签名）
    .signWith(SignatureAlgorithm.HS256, "my-secret-key-123456-userservice")
    // 用密钥对 Header + Payload 签名
    
    .compact();
    // 生成最终 Token

// 第 3 步：返回 Token
return {
    "code": 200,
    "message": "登录成功",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ.xxx"
}
```

### 2.2 Token 的生成细节

```
第 1 步：创建 Header
  {"alg":"HS256","typ":"JWT"}
  ↓ Base64 编码
  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9

第 2 步：创建 Payload
  {"userId":1,"username":"admin","iat":1706000000,"exp":1706007200}
  ↓ Base64 编码
  eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAsImV4cCI6MTcwNjAwNzIwMH0

第 3 步：计算签名
  data = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAsImV4cCI6MTcwNjAwNzIwMH0"
  signature = HMAC_SHA256(data, "my-secret-key-123456-userservice")
  ↓
  SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

第 4 步：拼接 Token
  Token = Header + "." + Payload + "." + Signature
  Token = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAsImV4cCI6MTcwNjAwNzIwMH0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

## 三、JWT 验证 Token 的详细过程

### 3.1 获取用户信息（验证 Token）

```java
// 用户请求
GET http://localhost:8081/user/info
Headers:
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ.xxx

// UserController.getUserInfo() 方法执行：

// 第 1 步：从请求头获取 Token
String authorization = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
String token = authorization.replace("Bearer ", "");
// token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

// 第 2 步：调用 JwtUtil.parseToken() 验证 Token
Claims claims = JwtUtil.parseToken(token);

// JwtUtil.parseToken() 内部执行：

// 2.1 拆分 Token
String[] parts = token.split("\\.");
String header = parts[0];     // eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
String payload = parts[1];    // eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ
String signature = parts[2];  // SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

// 2.2 验证签名
String data = header + "." + payload;
String newSignature = HMAC_SHA256(data, "my-secret-key-123456-userservice");

if (!signature.equals(newSignature)) {
    throw new Exception("签名不匹配，Token 被篡改");
}

// 2.3 Base64 解码 Payload
String payloadJson = Base64.decode(payload);
// {"userId":1,"username":"admin","iat":1706000000,"exp":1706007200}

// 2.4 检查过期时间
long exp = 1706007200;  // 从 Payload 中解析
long now = System.currentTimeMillis() / 1000;

if (now > exp) {
    throw new Exception("Token 已过期");
}

// 2.5 返回 Claims（包含用户信息）
return claims;

// 第 3 步：提取用户信息
Long userId = claims.get("userId", Long.class);      // 1
String username = claims.get("username", String.class); // admin

// 第 4 步：根据 userId 查询用户详细信息
User user = userService.queryById(userId);

// 第 5 步：返回用户信息
return {
    "code": 200,
    "message": "获取成功",
    "data": {
        "id": 1,
        "username": "admin",
        "address": "北京市"
    }
}
```

### 3.2 Token 验证细节

```
第 1 步：拆分 Token
  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
  ↓ 按 . 分割
  Header: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
  Payload: eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ
  Signature: SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

第 2 步：验证签名
  用密钥重新计算签名：
  newSignature = HMAC_SHA256(Header + "." + Payload, 密钥)
  
  对比：
  原签名：SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
  新签名：SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
  
  结果：一致 ✅ Token 没被篡改

第 3 步：解码 Payload
  eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ
  ↓ Base64 解码
  {"userId":1,"username":"admin","iat":1706000000,"exp":1706007200}

第 4 步：检查过期时间
  过期时间：1706007200 (2026-01-23 12:00:00)
  当前时间：1706003600 (2026-01-23 11:00:00)
  
  结果：当前时间 < 过期时间 ✅ 没过期

第 5 步：提取用户信息
  userId = 1
  username = admin
```

## 四、完整的测试流程

### 4.1 测试登录（生成 Token）

```bash
# 1. 发送登录请求
curl -X POST http://localhost:8081/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 2. 收到响应
{
  "code": 200,
  "message": "登录成功",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAsImV4cCI6MTcwNjAwNzIwMH0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
}

# 3. 保存 Token（在浏览器中）
localStorage.setItem('token', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...');
```

### 4.2 测试获取用户信息（验证 Token）

```bash
# 1. 发送请求（带 Token）
curl http://localhost:8081/user/info \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAsImV4cCI6MTcwNjAwNzIwMH0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

# 2. 收到响应
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "id": 1,
    "username": "柳岩",
    "address": "湖南省衡阳市"
  }
}
```

### 4.3 测试 Token 被篡改

```bash
# 1. 修改 Token（把 userId 从 1 改成 999）
# 原始 Token：eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ.xxx
# 修改后：eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjk5OSwidXNlcm5hbWUiOiJhZG1pbiJ9.xxx

# 2. 发送请求
curl http://localhost:8081/user/info \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjk5OSwidXNlcm5hbWUiOiJhZG1pbiJ9.xxx"

# 3. 收到错误响应
{
  "code": 401,
  "message": "Token 无效"
}

# 原因：签名对不上，Token 被篡改了
```

## 五、总结

### 5.1 生成 Token（UserService）

```
用户登录 → 验证密码 → 调用 JwtUtil.generateToken()
  ↓
准备用户数据（userId, username）
  ↓
Base64 编码 Header 和 Payload
  ↓
用密钥签名
  ↓
拼接成 Token
  ↓
返回给客户端
```

### 5.2 验证 Token（UserService）

```
客户端带 Token 请求 → 调用 JwtUtil.parseToken()
  ↓
拆分 Token（3部分）
  ↓
用密钥重新签名，对比是否一致
  ↓
检查是否过期
  ↓
Base64 解码 Payload，提取用户信息
  ↓
根据 userId 查询用户详细信息
  ↓
返回给客户端
```

### 5.3 关键点

1. **密钥保密**：密钥只有服务端知道，客户端不知道
2. **签名防篡改**：修改 Token 内容会导致签名对不上
3. **过期时间**：Token 有有效期，过期后需要重新登录
4. **不是加密**：Token 内容任何人都能看到（Base64 解码），不要放敏感信息

**一句话总结：** UserService 用 JWT 库生成 Token（用户信息+密钥签名），验证 Token 时用密钥重新签名对比，确保没被篡改，然后提取用户信息处理请求。
