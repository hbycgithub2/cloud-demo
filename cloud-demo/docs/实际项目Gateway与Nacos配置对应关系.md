# 实际项目 Gateway 与 Nacos 配置对应关系

## 一、项目现状分析

### 1.1 Gateway 本地配置（application.yml）

```yaml
server:
  port: 10010  # Gateway 监听端口

spring:
  application:
    name: gateway  # 服务名
  cloud:
    nacos:
      server-addr: localhost:8848  # Nacos 地址
    gateway:
      routes:
        # 本地配置了 2 个路由
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/user/**
        
        - id: order-service
          uri: lb://orderservice
          predicates:
            - Path=/order/**
```

**人话：** Gateway 本地只配置了 2 个路由（user 和 order）

### 1.2 Nacos 配置中心的配置

#### 配置 1：gateway-dev.yaml（YAML 格式）

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: admin
          uri: lb://admin
          predicates:
            - Path=/admin/**
          filters:
            - StripPrefix=1
        
        - id: user
          uri: lb://user
          predicates:
            - Path=/user/**
          filters:
            - StripPrefix=1
        
        - id: search
          uri: lb://search
          predicates:
            - Path=/search/**
          filters:
            - StripPrefix=1
        
        # ... 还有 manage, news, sms, atlas, ipr, sale 等路由
```

**人话：** Nacos 配置了 9 个路由（admin, user, search, manage, news, sms, atlas, ipr, sale）

#### 配置 2：gateway-router（JSON 格式）

```json
[
  {
    "id": "admin",
    "order": 0,
    "predicates": [{"args": {"pattern": "/admin/**"}, "name": "Path"}],
    "filters": [{"args": {"parts": "1"}, "name": "StripPrefix"}],
    "uri": "lb://admin"
  },
  {
    "id": "user",
    "order": 0,
    "predicates": [{"args": {"pattern": "/user/**"}, "name": "Path"}],
    "filters": [{"args": {"parts": "1"}, "name": "StripPrefix"}],
    "uri": "lb://user"
  }
  // ... 还有 search, news, sms, largeScreen, atlas, sale
]
```

**人话：** 和 gateway-dev.yaml 内容一样，只是格式不同（JSON vs YAML）

## 二、配置合并关系

### 2.1 配置优先级

```
Gateway 启动时的配置加载顺序：

1. 本地配置（application.yml）
   ├─ user-service: /user/** → lb://userservice
   └─ order-service: /order/** → lb://orderservice

2. Nacos 配置（gateway-dev.yaml）
   ├─ admin: /admin/** → lb://admin
   ├─ user: /user/** → lb://user
   ├─ search: /search/** → lb://search
   ├─ manage: /manage/** → lb://manage
   ├─ news: /news/** → lb://news
   ├─ sms: /sms/** → lb://sms
   ├─ atlas: /atlas/** → lb://atlas
   ├─ ipr: /ipr/** → lb://ipr
   └─ sale: /sale/** → lb://sale

3. 合并后的最终路由表
   ├─ user-service: /user/** → lb://userservice  (本地)
   ├─ order-service: /order/** → lb://orderservice  (本地)
   ├─ admin: /admin/** → lb://admin  (Nacos)
   ├─ user: /user/** → lb://user  (Nacos，可能覆盖本地)
   ├─ search: /search/** → lb://search  (Nacos)
   ├─ manage: /manage/** → lb://manage  (Nacos)
   ├─ news: /news/** → lb://news  (Nacos)
   ├─ sms: /sms/** → lb://sms  (Nacos)
   ├─ atlas: /atlas/** → lb://atlas  (Nacos)
   ├─ ipr: /ipr/** → lb://ipr  (Nacos)
   └─ sale: /sale/** → lb://sale  (Nacos)
```

**注意：** Nacos 配置优先级更高，会覆盖本地配置

## 三、完整的路由转发流程图

### 3.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         客户端                                   │
│                                                                 │
│  浏览器/APP 发起请求                                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 所有请求统一入口
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Gateway (端口 10010)                          │
│                                                                 │
│  路由表（本地配置 + Nacos 配置）：                                │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ /admin/**  → lb://admin                                   │ │
│  │ /user/**   → lb://user                                    │ │
│  │ /search/** → lb://search                                  │ │
│  │ /manage/** → lb://manage                                  │ │
│  │ /news/**   → lb://news                                    │ │
│  │ /sms/**    → lb://sms                                     │ │
│  │ /atlas/**  → lb://atlas                                   │ │
│  │ /ipr/**    → lb://ipr                                     │ │
│  │ /sale/**   → lb://sale                                    │ │
│  └───────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
         │          │          │          │          │
         │          │          │          │          │
    ┌────┘     ┌────┘     ┌────┘     ┌────┘     └────┐
    ↓          ↓          ↓          ↓               ↓
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐     ┌────────┐
│ admin  │ │  user  │ │ search │ │  news  │ ... │  sale  │
│ 服务   │ │  服务  │ │  服务  │ │  服务  │     │  服务  │
└────────┘ └────────┘ └────────┘ └────────┘     └────────┘
```

### 3.2 配置加载流程图

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：Gateway 启动                                           │
│                                                                 │
│  启动命令：java -jar gateway.jar                                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：读取本地配置（application.yml）                         │
│                                                                 │
│  加载路由：                                                      │
│    - user-service: /user/** → lb://userservice                 │
│    - order-service: /order/** → lb://orderservice              │
│                                                                 │
│  获取 Nacos 地址：localhost:8848                                 │
│  获取服务名：gateway                                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：连接 Nacos 配置中心                                     │
│                                                                 │
│  Gateway → Nacos：我是 gateway 服务，给我配置                    │
│  Nacos → Gateway：这是你的配置文件                               │
│    - gateway-dev.yaml                                           │
│    - gateway-router                                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：解析 Nacos 配置                                        │
│                                                                 │
│  从 gateway-dev.yaml 读取：                                      │
│    - admin: /admin/** → lb://admin                              │
│    - user: /user/** → lb://user                                 │
│    - search: /search/** → lb://search                           │
│    - manage: /manage/** → lb://manage                           │
│    - news: /news/** → lb://news                                 │
│    - sms: /sms/** → lb://sms                                    │
│    - atlas: /atlas/** → lb://atlas                              │
│    - ipr: /ipr/** → lb://ipr                                    │
│    - sale: /sale/** → lb://sale                                 │
│                                                                 │
│  还读取了其他配置：                                              │
│    - Redis 配置                                                 │
│    - 限流配置                                                   │
│    - IP 白名单                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：合并配置，生成最终路由表                                │
│                                                                 │
│  最终路由表（11 个路由）：                                        │
│    1. user-service: /user/** → lb://userservice (本地)         │
│    2. order-service: /order/** → lb://orderservice (本地)      │
│    3. admin: /admin/** → lb://admin (Nacos)                    │
│    4. user: /user/** → lb://user (Nacos，覆盖本地)              │
│    5. search: /search/** → lb://search (Nacos)                 │
│    6. manage: /manage/** → lb://manage (Nacos)                 │
│    7. news: /news/** → lb://news (Nacos)                       │
│    8. sms: /sms/** → lb://sms (Nacos)                          │
│    9. atlas: /atlas/** → lb://atlas (Nacos)                    │
│   10. ipr: /ipr/** → lb://ipr (Nacos)                          │
│   11. sale: /sale/** → lb://sale (Nacos)                       │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：Gateway 启动完成                                       │
│                                                                 │
│  监听端口：10010                                                 │
│  状态：Ready                                                    │
│  开始接收请求                                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 请求转发流程图（以 /admin/login 为例）

```
┌─────────────────────────────────────────────────────────────────┐
│  客户端                                                          │
│  发送请求：POST http://localhost:10010/admin/login              │
│  请求体：{"username":"admin","password":"123456"}               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ① 请求到达
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (端口 10010)                                            │
│                                                                 │
│  ② 接收请求                                                      │
│     路径：/admin/login                                           │
│     方法：POST                                                   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ③ 匹配路由
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 路由匹配                                                │
│                                                                 │
│  检查路由表：                                                    │
│    - admin: /admin/** → ✅ 匹配成功！                            │
│    - 目标：lb://admin                                           │
│    - 过滤器：StripPrefix=1 (去除 /admin 前缀)                   │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ④ 应用过滤器
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  StripPrefix 过滤器                                              │
│                                                                 │
│  原始路径：/admin/login                                          │
│  去除前缀：去掉 /admin                                           │
│  新路径：/login                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ⑤ 查询服务实例
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Nacos 服务注册中心                                              │
│                                                                 │
│  Gateway 查询：admin 服务有哪些实例？                            │
│                                                                 │
│  Nacos 返回：                                                    │
│    - 172.16.1.100:8080                                          │
│    - 172.16.1.101:8080                                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ⑥ 负载均衡选择
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway 负载均衡                                                │
│                                                                 │
│  算法：轮询                                                      │
│  选择：172.16.1.100:8080                                         │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ⑦ 转发请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Admin 服务 (172.16.1.100:8080)                                 │
│                                                                 │
│  接收请求：POST /login  ← 注意：路径已经去掉了 /admin            │
│  请求体：{"username":"admin","password":"123456"}               │
│                                                                 │
│  处理登录逻辑                                                    │
│  返回：{"token":"xxx","userId":1}                               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ⑧ 返回响应
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  Gateway (端口 10010)                                            │
│                                                                 │
│  接收响应并转发给客户端                                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ ⑨ 返回给客户端
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  客户端                                                          │
│  收到响应：{"token":"xxx","userId":1}                            │
└─────────────────────────────────────────────────────────────────┘
```

## 四、StripPrefix 过滤器详解

### 4.1 为什么需要 StripPrefix？

**问题场景：**
```
客户端请求：http://localhost:10010/admin/login
Gateway 转发：http://172.16.1.100:8080/admin/login

但是 Admin 服务的接口是：
  @PostMapping("/login")  ← 没有 /admin 前缀

结果：Admin 服务找不到 /admin/login 接口，返回 404
```

**解决方案：使用 StripPrefix 过滤器**
```
客户端请求：http://localhost:10010/admin/login
Gateway 应用 StripPrefix=1：去掉第 1 段路径 (/admin)
Gateway 转发：http://172.16.1.100:8080/login  ← 去掉了 /admin

Admin 服务接收：/login  ← 匹配成功！
```

### 4.2 StripPrefix 工作原理

```
StripPrefix=1 的含义：去掉路径的第 1 段

示例 1：
  原始路径：/admin/login
  分段：[admin, login]
  去掉第 1 段：[login]
  结果路径：/login

示例 2：
  原始路径：/admin/user/list
  分段：[admin, user, list]
  去掉第 1 段：[user, list]
  结果路径：/user/list

示例 3：
  原始路径：/admin/system/config/get
  分段：[admin, system, config, get]
  去掉第 1 段：[system, config, get]
  结果路径：/system/config/get
```

### 4.3 所有路由的 StripPrefix 效果

| 客户端请求路径 | 匹配路由 | StripPrefix | 转发到服务的路径 |
|--------------|---------|-------------|----------------|
| `/admin/login` | admin | 去掉 /admin | `/login` |
| `/user/info` | user | 去掉 /user | `/info` |
| `/search/query` | search | 去掉 /search | `/query` |
| `/news/list` | news | 去掉 /news | `/list` |
| `/sms/send` | sms | 去掉 /sms | `/send` |
| `/atlas/map` | atlas | 去掉 /atlas | `/map` |
| `/sale/order` | sale | 去掉 /sale | `/order` |

## 五、完整的路由对应表

### 5.1 路由配置对应表

| 路由 ID | 客户端请求路径 | 匹配规则 | 目标服务 | 过滤器 | 实际转发路径 |
|--------|--------------|---------|---------|-------|-------------|
| admin | `/admin/**` | Path=/admin/** | lb://admin | StripPrefix=1 | 去掉 /admin |
| user | `/user/**` | Path=/user/** | lb://user | StripPrefix=1 | 去掉 /user |
| search | `/search/**` | Path=/search/** | lb://search | StripPrefix=1 | 去掉 /search |
| manage | `/manage/**` | Path=/manage/** | lb://manage | StripPrefix=1 | 去掉 /manage |
| news | `/news/**` | Path=/news/** | lb://news | StripPrefix=1 | 去掉 /news |
| sms | `/sms/**` | Path=/sms/** | lb://sms | StripPrefix=1 | 去掉 /sms |
| atlas | `/atlas/**` | Path=/atlas/** | lb://atlas | StripPrefix=1 | 去掉 /atlas |
| ipr | `/ipr/**` | Path=/ipr/** | lb://ipr | StripPrefix=1 | 去掉 /ipr |
| sale | `/sale/**` | Path=/sale/** | lb://sale | StripPrefix=1 | 去掉 /sale |

### 5.2 实际转发示例

```
示例 1：管理员登录
  客户端 → Gateway：POST /admin/login
  Gateway → Admin 服务：POST /login

示例 2：用户信息查询
  客户端 → Gateway：GET /user/info?id=1
  Gateway → User 服务：GET /info?id=1

示例 3：搜索功能
  客户端 → Gateway：GET /search/query?keyword=测试
  Gateway → Search 服务：GET /query?keyword=测试

示例 4：新闻列表
  客户端 → Gateway：GET /news/list?page=1
  Gateway → News 服务：GET /list?page=1

示例 5：发送短信
  客户端 → Gateway：POST /sms/send
  Gateway → SMS 服务：POST /send
```

## 六、其他重要配置

### 6.1 Redis 配置

```yaml
spring:
  redis:
    open: true
    database: 3
    host: 172.16.1.186
    password: Qingce@20#21
    port: 6379
```

**作用：** 用于存储限流数据、会话信息等

### 6.2 限流配置

```yaml
gateway:
  rate-limit:
    enable: true
    enableIPRateLimit: true
    rules:
      - level: 1
        resources: /**
        time: 60
        count: 200
        operation: log
```

**人话：** 每个 IP 在 60 秒内最多访问 200 次，超过记录日志

### 6.3 IP 白名单

```yaml
white:
  ip: 47.94.80.13,172.17.48.209,39.96.61.65
  url: largeScreen/recp/homePage,search/mongodbUploadFile/uploadFile
```

**人话：** 这些 IP 和 URL 不受限流限制

## 七、总结（一句话版）

### 7.1 配置关系

> Gateway 本地配置了 2 个路由（user, order），Nacos 配置了 9 个路由（admin, user, search, manage, news, sms, atlas, ipr, sale），启动时合并成 11 个路由，Nacos 配置优先级更高。

### 7.2 转发流程

> 客户端请求 `/admin/login` → Gateway 匹配到 admin 路由 → 去掉 `/admin` 前缀 → 从 Nacos 查询 admin 服务实例 → 负载均衡选一个 → 转发 `/login` 到 Admin 服务 → 返回响应。

### 7.3 核心要点

1. **统一入口**：所有请求都通过 Gateway (10010 端口)
2. **路由匹配**：根据路径前缀匹配对应的服务
3. **去除前缀**：StripPrefix=1 去掉路径第一段
4. **负载均衡**：从 Nacos 获取服务实例列表并选择
5. **动态配置**：Nacos 配置可以动态更新，无需重启
