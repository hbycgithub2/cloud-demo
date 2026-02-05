# Gateway转发 + Token完整流程 - 超详细版

## 目录
1. [场景1：用户登录 - 生成Token](#场景1用户登录---生成token)
2. [场景2：访问用户信息 - 验证Token](#场景2访问用户信息---验证token)
3. [场景3：访问订单 - 验证Token](#场景3访问订单---验证token)
4. [Token生成的详细过程](#token生成的详细过程)
5. [Token解密的详细过程](#token解密的详细过程)

---

## 场景1：用户登录 - 生成Token

### 人话版流程

```
就像你去银行办卡：
1. 你去银行（访问登录接口）
2. 出示身份证（输入账号密码）
3. 银行验证你的身份（查数据库）
4. 银行给你一张银行卡（生成Token）
5. 你拿着卡回家（保存Token）
```

### 详细流程图

```
第1步：用户在浏览器输入账号密码
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  用户输入：                                                      │
│    账号：admin                                                   │
│    密码：123456                                                  │
│    点击"登录"按钮                                                │
│                                                                 │
│  JavaScript 发送请求：                                           │
│    POST http://localhost:10010/user/login                       │
│    Body: {"username":"admin", "password":"123456"}              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP请求
                            ↓

第2步：Gateway收到请求，开始路由匹配
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010)                                      │
│                                                                 │
│  收到请求：POST /user/login                                      │
│                                                                 │
│  开始匹配路由规则（application.yml配置）：                        │
│                                                                 │
│  routes:                                                        │
│    - id: user-service                                           │
│      uri: lb://userservice    ← lb = LoadBalance负载均衡        │
│      predicates:                                                │
│        - Path=/user/**        ← 匹配 /user/ 开头的路径          │
│                                                                 │
│  检查请求路径：/user/login                                       │
│    ✅ 匹配成功！符合 /user/** 规则                               │
│                                                                 │
│  目标服务：lb://userservice                                      │
│    lb = LoadBalance（负载均衡）                                  │
│    userservice = 服务名称                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 匹配成功
                            ↓

第3步：Gateway去Nacos查询userservice的实例地址
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 查询 Nacos                                              │
│                                                                 │
│  Gateway：Nacos，userservice服务有哪些实例？                     │
│                                                                 │
│  Nacos 返回：                                                    │
│    实例1：localhost:8081  ← 这是我们项目里实际运行的            │
│    实例2：localhost:8082  （如果有多个实例的话）                 │
│                                                                 │
│  Gateway 选择一个实例（负载均衡）：                              │
│    选中：localhost:8081                                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 确定目标地址
                            ↓

第4步：Gateway转发请求到UserService
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 转发                                                    │
│                                                                 │
│  原始请求：POST http://localhost:10010/user/login               │
│  转发到：  POST http://localhost:8081/user/login                │
│                                                                 │
│  请求体：{"username":"admin", "password":"123456"}              │
│                                                                 │
│  添加请求头（default-filters配置）：                             │
│    Truth: Itcast is freaking awesome!                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP请求
                            ↓

第5步：UserService收到请求，验证账号密码
┌─────────────────────────────────────────────────────────────────┐
│  UserService (localhost:8081)                                   │
│                                                                 │
│  @PostMapping("/login")                                         │
│  public Map<String, Object> login(String username, String pwd) {│
│                                                                 │
│      // 1. 接收参数                                             │
│      username = "admin"                                         │
│      password = "123456"                                        │
│                                                                 │
│      // 2. 查询数据库                                           │
│      User user = userMapper.findByUsername("admin");            │
│      // 查到：id=1, username=admin, password=123456             │
│                                                                 │
│      // 3. 验证密码                                             │
│      if (user.getPassword().equals("123456")) {                 │
│          // ✅ 密码正确                                          │
│      }                                                           │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 验证成功
                            ↓

第6步：UserService生成Token（重点！）
┌─────────────────────────────────────────────────────────────────┐
│  UserService 生成 Token                                          │
│                                                                 │
│  调用JWT库：                                                     │
│  String token = Jwts.builder()                                  │
│                                                                 │
│      // 第1部分：Header（头部）- 自动生成                        │
│      .setHeaderParam("alg", "HS256")  // 算法：HMAC-SHA256      │
│      .setHeaderParam("typ", "JWT")    // 类型：JWT              │
│      // 结果：{"alg":"HS256","typ":"JWT"}                       │
│      // Base64编码后：eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9      │
│                                                                 │
│      // 第2部分：Payload（载荷）- 用户数据                       │
│      .claim("userId", 1)              // 用户ID                 │
│      .claim("username", "admin")      // 用户名                 │
│      .setIssuedAt(new Date())         // 签发时间               │
│      .setExpiration(2小时后)           // 过期时间               │
│      // 结果：{"userId":1,"username":"admin","iat":...,"exp":...}│
│      // Base64编码后：eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ│
│                                                                 │
│      // 第3部分：Signature（签名）- 防篡改                       │
│      .signWith(SignatureAlgorithm.HS256, "my-secret-key")      │
│      // 用密钥对前两部分签名                                     │
│      // 签名 = HMAC_SHA256(Header + "." + Payload, 密钥)        │
│      // 结果：SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c       │
│                                                                 │
│      .compact();  // 拼接成完整Token                            │
│                                                                 │
│  最终Token = 第1部分.第2部分.第3部分                             │
│  Token = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.                  │
│          eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4ifQ.            │
│          SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ Token生成完成
                            ↓

第7步：UserService返回Token
┌─────────────────────────────────────────────────────────────────┐
│  UserService 返回响应                                            │
│                                                                 │
│  return {                                                       │
│      "code": 200,                                               │
│      "message": "登录成功",                                      │
│      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQi│
│                OjEsInVzZXJuYW1lIjoiYWRtaW4ifQ.SflKxwRJSMeKKF2QT4│
│                fwpMeJf36POk6yJV_adQssw5c"                       │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP响应
                            ↓

第8步：Gateway转发响应给浏览器
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 转发响应                                                │
│                                                                 │
│  UserService返回的响应 → Gateway → 浏览器                        │
│  （Gateway不修改响应内容，直接转发）                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP响应
                            ↓

第9步：浏览器保存Token
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  收到响应：                                                      │
│  {                                                              │
│    "code": 200,                                                 │
│    "message": "登录成功",                                        │
│    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."          │
│  }                                                              │
│                                                                 │
│  JavaScript保存Token：                                           │
│  localStorage.setItem('token', response.token);                 │
│                                                                 │
│  就像把银行卡放进钱包，以后用                                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 场景2：访问用户信息 - 验证Token

### 人话版流程

```
就像你去银行取钱：
1. 你拿出银行卡（带上Token）
2. 门卫检查你的卡（Gateway验证Token）
3. 卡是真的，门卫放行（验证通过）
4. 门卫告诉柜员你的账号（传递userId）
5. 柜员查你的账户（UserService查数据库）
6. 给你钱（返回用户信息）
```

### 详细流程图

```
第1步：用户点击"个人中心"
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  用户点击"个人中心"按钮                                          │
│                                                                 │
│  JavaScript取出Token：                                           │
│  const token = localStorage.getItem('token');                   │
│  // token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."          │
│                                                                 │
│  发送请求：                                                      │
│  GET http://localhost:10010/user/1                              │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...│
│                                                                 │
│  就像拿出银行卡                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP请求（带Token）
                            ↓

第2步：Gateway收到请求，匹配路由
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010)                                      │
│                                                                 │
│  收到请求：GET /user/1                                           │
│  Headers: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI...  │
│                                                                 │
│  匹配路由：                                                      │
│    路径 /user/1 匹配 /user/** ✅                                 │
│    目标：lb://userservice                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 路由匹配成功
                            ↓

第3步：Gateway验证Token（重点！）
┌─────────────────────────────────────────────────────────────────┐
│  Gateway - AuthorizeFilter（鉴权过滤器）                         │
│                                                                 │
│  注意：当前项目中这个过滤器被注释掉了（@Component被注释）        │
│  所以实际不会验证Token，直接放行                                 │
│                                                                 │
│  但如果启用了，会这样验证：                                      │
│                                                                 │
│  public Mono<Void> filter(ServerWebExchange exchange, ...) {    │
│                                                                 │
│      // 3.1 从请求头获取Token                                   │
│      String auth = exchange.getRequest()                        │
│          .getHeaders()                                          │
│          .getFirst("Authorization");                            │
│      // auth = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." │
│                                                                 │
│      if (auth == null || !auth.startsWith("Bearer ")) {         │
│          // 没有Token，返回401                                  │
│          return unauthorized();                                 │
│      }                                                           │
│                                                                 │
│      // 3.2 去掉"Bearer "前缀                                   │
│      String token = auth.substring(7);                          │
│      // token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."      │
│                                                                 │
│      try {                                                      │
│          // 3.3 验证Token（调用JWT库）                          │
│          Claims claims = Jwts.parser()                          │
│              .setSigningKey("my-secret-key")  // 用密钥验证     │
│              .parseClaimsJws(token)           // 解析Token      │
│              .getBody();                      // 获取Payload    │
│                                                                 │
│          // JWT库内部做了什么？见下面详细说明                    │
│                                                                 │
│          // 3.4 提取用户信息                                    │
│          Integer userId = claims.get("userId", Integer.class);  │
│          String username = claims.get("username", String.class);│
│          // userId = 1                                          │
│          // username = "admin"                                  │
│                                                                 │
│          // 3.5 把用户信息添加到请求头                          │
│          ServerHttpRequest request = exchange.getRequest()      │
│              .mutate()                                          │
│              .header("X-User-Id", "1")                          │
│              .header("X-Username", "admin")                     │
│              .build();                                          │
│                                                                 │
│          // 3.6 放行，继续转发                                  │
│          return chain.filter(exchange.mutate()                  │
│              .request(request).build());                        │
│                                                                 │
│      } catch (Exception e) {                                    │
│          // Token无效或过期，返回401                            │
│          return unauthorized();                                 │
│      }                                                           │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ Token验证通过
                            ↓

第4步：Gateway去Nacos查询userservice实例
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 查询 Nacos                                              │
│                                                                 │
│  Gateway：userservice有哪些实例？                                │
│  Nacos：localhost:8081                                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓

第5步：Gateway转发请求到UserService
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 转发                                                    │
│                                                                 │
│  原始请求：GET http://localhost:10010/user/1                    │
│  转发到：  GET http://localhost:8081/user/1                     │
│                                                                 │
│  请求头：                                                        │
│    Truth: Itcast is freaking awesome!  ← default-filters添加    │
│    X-User-Id: 1                        ← AuthorizeFilter添加    │
│    X-Username: admin                   ← AuthorizeFilter添加    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP请求
                            ↓

第6步：UserService处理请求
┌─────────────────────────────────────────────────────────────────┐
│  UserService (localhost:8081)                                   │
│                                                                 │
│  @GetMapping("/{id}")                                           │
│  public User queryById(                                         │
│      @PathVariable("id") Long id,                               │
│      @RequestHeader("Truth") String truth,                      │
│      @RequestHeader("X-User-Id") Integer userId) {              │
│                                                                 │
│      // 接收参数                                                │
│      id = 1                    // 从路径参数获取                │
│      truth = "Itcast is freaking awesome!"  // 从请求头获取     │
│      userId = 1                // 从请求头获取（Gateway传的）   │
│                                                                 │
│      // 查询数据库                                              │
│      User user = userMapper.selectById(id);                     │
│      // 查到：id=1, username=柳岩, address=湖南省衡阳市         │
│                                                                 │
│      // 返回用户信息                                            │
│      return user;                                               │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回用户信息
                            ↓

第7步：UserService返回响应
┌─────────────────────────────────────────────────────────────────┐
│  UserService 返回                                                │
│                                                                 │
│  {                                                              │
│    "id": 1,                                                     │
│    "username": "柳岩",                                           │
│    "address": "湖南省衡阳市"                                     │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP响应
                            ↓

第8步：Gateway转发响应给浏览器
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 转发响应                                                │
│                                                                 │
│  UserService的响应 → Gateway → 浏览器                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP响应
                            ↓

第9步：浏览器显示用户信息
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  收到数据：                                                      │
│  {                                                              │
│    "id": 1,                                                     │
│    "username": "柳岩",                                           │
│    "address": "湖南省衡阳市"                                     │
│  }                                                              │
│                                                                 │
│  显示在页面上                                                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 场景3：访问订单 - 验证Token

### 详细流程图

```
第1步：用户点击"我的订单"
┌─────────────────────────────────────────────────────────────────┐
│  浏览器                                                          │
│                                                                 │
│  用户点击"我的订单"按钮                                          │
│                                                                 │
│  取出Token：                                                     │
│  const token = localStorage.getItem('token');                   │
│                                                                 │
│  发送请求：                                                      │
│  GET http://localhost:10010/order/101                           │
│  Headers:                                                       │
│    Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...│
│                                                                 │
│  还是同一个Token（同一张银行卡）                                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP请求（带Token）
                            ↓

第2步：Gateway收到请求，匹配路由
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (localhost:10010)                                      │
│                                                                 │
│  收到请求：GET /order/101                                        │
│                                                                 │
│  匹配路由：                                                      │
│    routes:                                                      │
│      - id: order-service                                        │
│        uri: lb://orderservice                                   │
│        predicates:                                              │
│          - Path=/order/**                                       │
│                                                                 │
│  路径 /order/101 匹配 /order/** ✅                               │
│  目标：lb://orderservice                                         │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 路由匹配成功
                            ↓

第3步：Gateway验证Token（和场景2一样）
┌─────────────────────────────────────────────────────────────────┐
│  Gateway - AuthorizeFilter                                      │
│                                                                 │
│  验证Token → 提取userId=1 → 添加到请求头                         │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ Token验证通过
                            ↓

第4步：Gateway去Nacos查询orderservice实例
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 查询 Nacos                                              │
│                                                                 │
│  Gateway：orderservice有哪些实例？                               │
│  Nacos：localhost:8082                                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓

第5步：Gateway转发请求到OrderService
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 转发                                                    │
│                                                                 │
│  原始请求：GET http://localhost:10010/order/101                 │
│  转发到：  GET http://localhost:8082/order/101                  │
│                                                                 │
│  请求头：                                                        │
│    Truth: Itcast is freaking awesome!                           │
│    X-User-Id: 1          ← Gateway从Token中提取的               │
│    X-Username: admin     ← Gateway从Token中提取的               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ HTTP请求
                            ↓

第6步：OrderService处理请求
┌─────────────────────────────────────────────────────────────────┐
│  OrderService (localhost:8082)                                  │
│                                                                 │
│  @GetMapping("/{orderId}")                                      │
│  public Order queryOrderById(                                   │
│      @PathVariable("orderId") Long orderId,                     │
│      @RequestHeader("X-User-Id") Integer userId) {              │
│                                                                 │
│      // 接收参数                                                │
│      orderId = 101         // 从路径参数获取                    │
│      userId = 1            // 从请求头获取（Gateway传的）       │
│                                                                 │
│      // 查询订单                                                │
│      Order order = orderMapper.selectById(orderId);             │
│      // 查到：id=101, userId=1, price=999                       │
│                                                                 │
│      // 验证订单是否属于当前用户                                 │
│      if (order.getUserId() != userId) {                         │
│          throw new Exception("无权访问");                        │
│      }                                                           │
│                                                                 │
│      // 返回订单信息                                            │
│      return order;                                              │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回订单信息
                            ↓

第7步：OrderService返回响应 → Gateway转发 → 浏览器显示
┌─────────────────────────────────────────────────────────────────┐
│  浏览器收到订单信息：                                            │
│  {                                                              │
│    "id": 101,                                                   │
│    "userId": 1,                                                 │
│    "name": "苹果手机",                                           │
│    "price": 999                                                 │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## Token生成的详细过程

### 人话版

```
就像制作一张防伪银行卡：
1. 准备卡片信息（用户ID、用户名）
2. 写在卡片上（Payload）
3. 盖上银行的防伪章（签名）
4. 卡片完成
```

### 详细步骤

```
┌─────────────────────────────────────────────────────────────────┐
│  第1步：准备数据                                                 │
│                                                                 │
│  用户信息：                                                      │
│    userId: 1                                                    │
│    username: "admin"                                            │
│                                                                 │
│  时间信息：                                                      │
│    iat (签发时间): 1706000000  (2026-01-23 10:00:00)            │
│    exp (过期时间): 1706007200  (2026-01-23 12:00:00, 2小时后)   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第2步：创建Header（头部）                                       │
│                                                                 │
│  Header JSON：                                                  │
│  {                                                              │
│    "alg": "HS256",    // 算法：HMAC-SHA256                      │
│    "typ": "JWT"       // 类型：JWT                              │
│  }                                                              │
│                                                                 │
│  Base64编码：                                                    │
│  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓

┌─────────────────────────────────────────────────────────────────┐
│  第3步：创建Payload（载荷）                                      │
│                                                                 │
│  Payload JSON：                                                 │
│  {                                                              │
│    "userId": 1,                                                 │
│    "username": "admin",                                         │
│    "iat": 1706000000,                                           │
│    "exp": 1706007200                                            │
│  }                                                              │
│                                                                 │
│  Base64编码：                                                    │
│  eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAs│
│  ImV4cCI6MTcwNjAwNzIwMH0                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第4步：计算签名（Signature）- 防篡改的关键！                    │
│                                                                 │
│  签名算法：HMAC-SHA256                                           │
│                                                                 │
│  输入数据：                                                      │
│    data = Header的Base64 + "." + Payload的Base64                │
│    data = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEs│
│            InVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAsImV4c│
│            CI6MTcwNjAwNzIwMH0"                                  │
│                                                                 │
│  密钥：                                                          │
│    secretKey = "my-secret-key-123456"                           │
│    （这个密钥只有服务器知道，客户端不知道）                      │
│                                                                 │
│  计算签名：                                                      │
│    signature = HMAC_SHA256(data, secretKey)                     │
│    signature = SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c     │
│                                                                 │
│  签名的作用：                                                    │
│    就像银行的防伪章，只有银行能盖，别人盖不了                    │
│    如果有人改了卡片内容，签名就对不上了                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第5步：拼接成完整Token                                          │
│                                                                 │
│  Token = Header + "." + Payload + "." + Signature               │
│                                                                 │
│  Token = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.                  │
│          eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDY│
│          wMDAwMDAsImV4cCI6MTcwNjAwNzIwMH0.                      │
│          SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c           │
│                                                                 │
│  Token结构：                                                     │
│    第1部分：Header（说明书）                                     │
│    第2部分：Payload（用户信息）                                  │
│    第3部分：Signature（防伪章）                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Java代码实现

```java
// UserService中生成Token的代码

String token = Jwts.builder()
    // Header
    .setHeaderParam("alg", "HS256")
    .setHeaderParam("typ", "JWT")
    
    // Payload
    .claim("userId", 1)
    .claim("username", "admin")
    .setIssuedAt(new Date())                    // 签发时间
    .setExpiration(new Date(System.currentTimeMillis() + 7200000))  // 过期时间
    
    // Signature
    .signWith(SignatureAlgorithm.HS256, "my-secret-key-123456")
    
    // 生成Token
    .compact();
```

---

## Token解密的详细过程

### 人话版

```
就像银行验证你的银行卡：
1. 拿到卡片（收到Token）
2. 检查防伪章（验证签名）
3. 看卡片是否过期（检查过期时间）
4. 读取卡片信息（提取用户ID）
5. 确认是真卡，放行
```

### 详细步骤

```
┌─────────────────────────────────────────────────────────────────┐
│  第1步：收到Token                                                │
│                                                                 │
│  从请求头获取：                                                  │
│  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1│
│                        c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpY│
│                        XQiOjE3MDYwMDAwMDAsImV4cCI6MTcwNjAwNzIwMH│
│                        0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQs│
│                        sw5c                                     │
│                                                                 │
│  去掉"Bearer "前缀：                                             │
│  token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEs│
│           InVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAsImV4c│
│           CI6MTcwNjAwNzIwMH0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV│
│           _adQssw5c"                                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第2步：拆分Token（按.分割）                                     │
│                                                                 │
│  String[] parts = token.split("\\.");                           │
│                                                                 │
│  parts[0] = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"             │
│             ↑ Header                                            │
│                                                                 │
│  parts[1] = "eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE│
│              3MDYwMDAwMDAsImV4cCI6MTcwNjAwNzIwMH0"              │
│             ↑ Payload                                           │
│                                                                 │
│  parts[2] = "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"     │
│             ↑ Signature（原始签名）                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓

┌─────────────────────────────────────────────────────────────────┐
│  第3步：验证签名（防篡改检查）- 最关键的一步！                   │
│                                                                 │
│  3.1 重新计算签名                                               │
│      data = parts[0] + "." + parts[1]                           │
│      data = Header + "." + Payload                              │
│                                                                 │
│      newSignature = HMAC_SHA256(data, "my-secret-key-123456")   │
│      newSignature = SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c │
│                                                                 │
│  3.2 对比签名                                                   │
│      原始签名：SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c      │
│      新签名：  SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c      │
│                                                                 │
│      if (原始签名 == 新签名) {                                  │
│          ✅ 签名一致，Token是真的，没被改过                      │
│      } else {                                                   │
│          ❌ 签名不一致，Token被篡改了，拒绝！                    │
│      }                                                           │
│                                                                 │
│  为什么黑客改不了Token？                                         │
│                                                                 │
│  假设黑客想把userId从1改成999：                                  │
│                                                                 │
│  原始Token：                                                     │
│    Payload: {"userId":1,"username":"admin"}                     │
│    Signature: SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c      │
│                                                                 │
│  黑客改成：                                                      │
│    Payload: {"userId":999,"username":"admin"}  ← 改了           │
│    Signature: SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c  ← 没改│
│                                                                 │
│  服务器验证：                                                    │
│    用密钥重新计算签名：                                          │
│    newSignature = HMAC_SHA256(新Payload, 密钥)                  │
│    newSignature = XYZ789...（和原签名不一样）                    │
│                                                                 │
│    对比：                                                        │
│    原签名：SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c          │
│    新签名：XYZ789...                                             │
│    不一致！拒绝！                                                │
│                                                                 │
│  黑客为什么算不出正确的签名？                                    │
│    因为黑客不知道密钥"my-secret-key-123456"                      │
│    只有服务器知道密钥                                            │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 签名验证通过
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第4步：Base64解码Payload                                        │
│                                                                 │
│  Payload Base64：                                               │
│  eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3MDYwMDAwMDAs│
│  ImV4cCI6MTcwNjAwNzIwMH0                                        │
│                                                                 │
│  Base64解码后：                                                  │
│  {                                                              │
│    "userId": 1,                                                 │
│    "username": "admin",                                         │
│    "iat": 1706000000,                                           │
│    "exp": 1706007200                                            │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第5步：检查过期时间                                             │
│                                                                 │
│  从Payload中读取：                                               │
│    exp = 1706007200  (2026-01-23 12:00:00)                      │
│                                                                 │
│  当前时间：                                                      │
│    now = 1706003600  (2026-01-23 11:00:00)                      │
│                                                                 │
│  对比：                                                          │
│    if (now < exp) {                                             │
│        ✅ 没过期，继续                                           │
│    } else {                                                     │
│        ❌ 过期了，拒绝！                                         │
│    }                                                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 没过期
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第6步：提取用户信息                                             │
│                                                                 │
│  从Payload中读取：                                               │
│    userId = 1                                                   │
│    username = "admin"                                           │
│                                                                 │
│  现在知道是哪个用户了！                                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 验证完成
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第7步：使用用户信息                                             │
│                                                                 │
│  Gateway：                                                       │
│    把userId添加到请求头：X-User-Id: 1                            │
│    转发给后端服务                                                │
│                                                                 │
│  后端服务：                                                      │
│    从请求头获取userId                                            │
│    根据userId查询数据库                                          │
│    返回数据                                                      │
└─────────────────────────────────────────────────────────────────┘
```

### Java代码实现

```java
// Gateway中验证Token的代码

try {
    // 解析Token
    Claims claims = Jwts.parser()
        .setSigningKey("my-secret-key-123456")  // 用密钥验证签名
        .parseClaimsJws(token)                  // 解析Token（内部会验证签名和过期时间）
        .getBody();                             // 获取Payload
    
    // 提取用户信息
    Integer userId = claims.get("userId", Integer.class);
    String username = claims.get("username", String.class);
    
    // 使用用户信息
    System.out.println("用户ID: " + userId);
    System.out.println("用户名: " + username);
    
} catch (SignatureException e) {
    // 签名验证失败，Token被篡改
    System.out.println("Token无效：签名不匹配");
    
} catch (ExpiredJwtException e) {
    // Token过期
    System.out.println("Token已过期");
    
} catch (Exception e) {
    // 其他错误
    System.out.println("Token解析失败");
}
```

---

## 完整流程总结图

### 登录流程（生成Token）

```
浏览器                Gateway              Nacos               UserService
  │                     │                    │                     │
  │ 1.POST /user/login  │                    │                     │
  │────────────────────>│                    │                     │
  │                     │                    │                     │
  │                     │ 2.匹配路由         │                     │
  │                     │   /user/** ✅      │                     │
  │                     │                    │                     │
  │                     │ 3.查询userservice  │                     │
  │                     │────────────────────>│                     │
  │                     │                    │                     │
  │                     │ 4.返回:8081        │                     │
  │                     │<────────────────────│                     │
  │                     │                    │                     │
  │                     │ 5.转发到8081/user/login                  │
  │                     │──────────────────────────────────────────>│
  │                     │                    │                     │
  │                     │                    │  6.验证账号密码      │
  │                     │                    │     查数据库 ✅      │
  │                     │                    │                     │
  │                     │                    │  7.生成Token        │
  │                     │                    │     userId=1        │
  │                     │                    │     +密钥签名       │
  │                     │                    │     =Token          │
  │                     │                    │                     │
  │                     │ 8.返回Token        │                     │
  │                     │<──────────────────────────────────────────│
  │                     │                    │                     │
  │ 9.返回Token         │                    │                     │
  │<────────────────────│                    │                     │
  │                     │                    │                     │
  │ 10.保存Token        │                    │                     │
  │ localStorage.set()  │                    │                     │
  │                     │                    │                     │
```

### 访问流程（验证Token）

```
浏览器                Gateway              Nacos               UserService
  │                     │                    │                     │
  │ 1.GET /user/1       │                    │                     │
  │   +Token            │                    │                     │
  │────────────────────>│                    │                     │
  │                     │                    │                     │
  │                     │ 2.匹配路由         │                     │
  │                     │   /user/** ✅      │                     │
  │                     │                    │                     │
  │                     │ 3.验证Token        │                     │
  │                     │   拆分Token        │                     │
  │                     │   验证签名 ✅      │                     │
  │                     │   检查过期 ✅      │                     │
  │                     │   提取userId=1     │                     │
  │                     │                    │                     │
  │                     │ 4.查询userservice  │                     │
  │                     │────────────────────>│                     │
  │                     │                    │                     │
  │                     │ 5.返回:8081        │                     │
  │                     │<────────────────────│                     │
  │                     │                    │                     │
  │                     │ 6.转发到8081/user/1                      │
  │                     │   +X-User-Id:1     │                     │
  │                     │──────────────────────────────────────────>│
  │                     │                    │                     │
  │                     │                    │  7.查数据库         │
  │                     │                    │    WHERE id=1       │
  │                     │                    │                     │
  │                     │ 8.返回用户信息     │                     │
  │                     │<──────────────────────────────────────────│
  │                     │                    │                     │
  │ 9.返回用户信息      │                    │                     │
  │<────────────────────│                    │                     │
  │                     │                    │                     │
  │ 10.显示在页面       │                    │                     │
  │                     │                    │                     │
```

---

## 关键点总结

### 1. Gateway的转发过程

```
收到请求 → 匹配路由规则 → 查询Nacos获取服务实例 → 转发请求
```

### 2. Token的生成过程

```
用户信息 → Base64编码 → 用密钥签名 → 拼接成Token
```

### 3. Token的验证过程

```
收到Token → 拆分3部分 → 验证签名 → 检查过期 → 提取用户信息
```

### 4. 为什么安全？

```
签名 = 防伪章
只有服务器知道密钥
黑客改了内容，签名就对不上
所以Token无法被篡改
```

### 5. 完整流程（一句话）

```
登录：浏览器 → Gateway转发 → UserService验证密码并生成Token → 返回Token
访问：浏览器带Token → Gateway验证Token并提取userId → 转发给后端 → 后端查数据库返回
```
