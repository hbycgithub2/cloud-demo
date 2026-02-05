# Gateway 请求转发 - 超简单版

## 一、人话版（一句话）

**Gateway 就像快递分拣中心：**
- 收到包裹（请求）
- 看地址（路径）
- 查规则（路由配置）
- 找快递站（从 Nacos 查服务地址）
- 送快递（转发请求）

---

## 二、完整流程图

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：客户端发送请求                                         │
│                                                                 │
│  浏览器输入：http://localhost:10010/user/1                      │
│  点击回车                                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ GET /user/1
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Gateway 收到请求                                       │
│                                                                 │
│  Gateway (localhost:10010)                                      │
│                                                                 │
│  收到：GET /user/1                                               │
│                                                                 │
│  Gateway 想：这个请求要转发到哪里？                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：匹配路由规则                                           │
│                                                                 │
│  Gateway 查看配置文件（application.yml）：                       │
│                                                                 │
│  routes:                                                        │
│    - id: user-service                                           │
│      uri: lb://userservice      ← 目标服务                      │
│      predicates:                                                │
│        - Path=/user/**          ← 匹配规则                      │
│                                                                 │
│  Gateway 想：                                                    │
│    请求路径是 /user/1                                            │
│    匹配规则是 /user/**                                           │
│    /user/1 符合 /user/** 吗？                                   │
│    ✅ 符合！                                                     │
│                                                                 │
│  Gateway 决定：转发到 lb://userservice                           │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：解析目标地址                                           │
│                                                                 │
│  Gateway 看到：lb://userservice                                  │
│                                                                 │
│  Gateway 想：                                                    │
│    lb = LoadBalance（负载均衡）                                  │
│    userservice = 服务名                                          │
│    我需要去 Nacos 查 userservice 的地址                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 查询服务地址
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：从 Nacos 查询服务地址                                  │
│                                                                 │
│  Gateway → Nacos：userservice 在哪里？                           │
│                                                                 │
│  Nacos 查注册表：                                                │
│    服务名：userservice                                           │
│    实例列表：                                                    │
│      - localhost:8081 ✅ 健康                                   │
│                                                                 │
│  Nacos → Gateway：在 localhost:8081                              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回地址
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：负载均衡（选择实例）                                    │
│                                                                 │
│  Gateway 收到实例列表：                                          │
│    - localhost:8081                                             │
│                                                                 │
│  Gateway 想：                                                    │
│    只有 1 个实例，就选它了                                       │
│    （如果有多个，就轮流选）                                      │
│                                                                 │
│  Gateway 决定：转发到 localhost:8081                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 7 步：转发请求                                               │
│                                                                 │
│  Gateway 发送请求：                                              │
│    GET http://localhost:8081/user/1                             │
│                                                                 │
│  就像快递员把包裹送到目的地                                       │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 8 步：UserService 处理请求                                   │
│                                                                 │
│  UserService (localhost:8081)                                   │
│                                                                 │
│  收到：GET /user/1                                               │
│                                                                 │
│  UserController.queryById(1)                                    │
│    查数据库：SELECT * FROM user WHERE id = 1                     │
│    查到数据：{"id":1,"username":"柳岩","address":"湖南"}         │
│                                                                 │
│  返回数据                                                        │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回数据
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 9 步：Gateway 接收响应                                       │
│                                                                 │
│  Gateway 收到：{"id":1,"username":"柳岩","address":"湖南"}       │
│                                                                 │
│  Gateway 想：好的，我把这个返回给客户端                          │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回响应
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 10 步：客户端收到数据                                        │
│                                                                 │
│  浏览器显示：{"id":1,"username":"柳岩","address":"湖南"}         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、简化版流程图

```
客户端
  │
  │ ① 发送请求：GET /user/1
  ↓
Gateway (10010)
  │
  │ ② 匹配路由：/user/** → userservice
  │ ③ 查询 Nacos：userservice 在哪？
  │ ④ Nacos 返回：localhost:8081
  │ ⑤ 转发请求：GET http://localhost:8081/user/1
  ↓
UserService (8081)
  │
  │ ⑥ 查数据库
  │ ⑦ 返回数据
  ↓
Gateway
  │
  │ ⑧ 转发响应
  ↓
客户端
  │
  │ ⑨ 显示数据
```

---

## 四、关键步骤详解

### 步骤 1：客户端发送请求

```
浏览器输入：http://localhost:10010/user/1

分解：
  - 协议：http://
  - 主机：localhost
  - 端口：10010  ← Gateway 的端口
  - 路径：/user/1
```

### 步骤 2：Gateway 收到请求

```
Gateway 收到：
  方法：GET
  路径：/user/1
  
Gateway 想：我要把这个请求转发到哪里？
```

### 步骤 3：匹配路由规则

```
Gateway 的配置：
  routes:
    - id: user-service
      uri: lb://userservice
      predicates:
        - Path=/user/**

匹配过程：
  请求路径：/user/1
  匹配规则：/user/**
  
  /user/1 是不是以 /user/ 开头？
  ✅ 是的！匹配成功！
  
  目标：lb://userservice
```

### 步骤 4：解析 lb://userservice

```
lb://userservice 是什么意思？

lb = LoadBalance（负载均衡）
  意思：这是一个需要负载均衡的服务
  
userservice = 服务名
  意思：服务的名字叫 userservice
  
Gateway 需要做什么？
  去 Nacos 查询 userservice 的地址
```

### 步骤 5：从 Nacos 查询

```
Gateway → Nacos：
  请求：给我 userservice 的地址
  
Nacos → Gateway：
  响应：userservice 在 localhost:8081
  
就像查电话簿：
  查询：张三的电话是多少？
  回答：张三的电话是 138xxxx
```

### 步骤 6：负载均衡

```
如果只有 1 个实例：
  直接选它
  
如果有多个实例：
  实例 1：localhost:8081
  实例 2：localhost:8082
  实例 3：localhost:8083
  
  轮流选择：
    第 1 次请求 → 实例 1
    第 2 次请求 → 实例 2
    第 3 次请求 → 实例 3
    第 4 次请求 → 实例 1
    ...
```

### 步骤 7：转发请求

```
原始请求：
  http://localhost:10010/user/1
  
转发后：
  http://localhost:8081/user/1
  
变化：
  - 主机和端口变了：10010 → 8081
  - 路径不变：/user/1
```

### 步骤 8：UserService 处理

```
UserService 收到：GET /user/1

UserController：
  @GetMapping("/{id}")
  public User queryById(@PathVariable Long id) {
      return userService.queryById(id);
  }

查数据库：
  SELECT * FROM user WHERE id = 1
  
返回：
  {"id":1,"username":"柳岩","address":"湖南"}
```

---

## 五、对比图

### 直接访问 UserService

```
客户端 → UserService (8081)
  
问题：
  - 客户端需要知道 UserService 的地址
  - 地址变了，客户端也要改
  - 没有统一的入口
```

### 通过 Gateway 访问

```
客户端 → Gateway (10010) → UserService (8081)
  
优点：
  - 客户端只需要知道 Gateway 的地址
  - UserService 地址变了，只需要在 Nacos 更新
  - 统一入口，方便管理
```

---

## 六、配置文件对应

### Gateway 配置（application.yml）

```yaml
server:
  port: 10010  # Gateway 端口

spring:
  cloud:
    gateway:
      routes:
        - id: user-service           # 路由 ID
          uri: lb://userservice      # 目标服务
          predicates:
            - Path=/user/**          # 匹配规则
```

**人话翻译：**
- 如果请求路径是 `/user/**`
- 就转发到 `userservice` 服务
- 从 Nacos 查询 `userservice` 的地址

### UserService 配置（application.yml）

```yaml
server:
  port: 8081  # UserService 端口

spring:
  application:
    name: userservice  # 服务名
  cloud:
    nacos:
      server-addr: localhost:8848  # Nacos 地址
```

**人话翻译：**
- 我的端口是 8081
- 我的名字叫 userservice
- 我会注册到 Nacos

---

## 七、实际测试

### 测试 1：直接访问 UserService

```bash
# 直接访问 UserService（不通过 Gateway）
curl http://localhost:8081/user/1

# 结果：
{"id":1,"username":"柳岩","address":"湖南省衡阳市"}
```

### 测试 2：通过 Gateway 访问

```bash
# 通过 Gateway 访问
curl http://localhost:10010/user/1

# 结果：
{"id":1,"username":"柳岩","address":"湖南省衡阳市"}

# 数据一样，但是走了 Gateway
```

### 测试 3：查看转发过程

```
客户端请求：
  http://localhost:10010/user/1
  
Gateway 日志：
  收到请求：GET /user/1
  匹配路由：user-service
  查询 Nacos：userservice
  转发到：http://localhost:8081/user/1
  
UserService 日志：
  收到请求：GET /user/1
  查询用户：id=1
  返回数据
```

---

## 八、总结

### 完整流程（10步）

1. 客户端发送请求：`GET http://localhost:10010/user/1`
2. Gateway 收到请求
3. 匹配路由规则：`/user/**` → `userservice`
4. 解析目标：`lb://userservice`
5. 查询 Nacos：`userservice` 在哪？
6. Nacos 返回：`localhost:8081`
7. 负载均衡：选择 `localhost:8081`
8. 转发请求：`GET http://localhost:8081/user/1`
9. UserService 处理并返回数据
10. Gateway 转发响应给客户端

### 关键点

- **路由匹配**：根据路径找到对应的服务
- **服务发现**：从 Nacos 查询服务地址
- **负载均衡**：多个实例时轮流选择
- **请求转发**：把请求发送到目标服务

### 一句话总结

**Gateway 就像快递分拣中心：收到包裹（请求）→ 看地址（路径）→ 查规则（路由配置）→ 找快递站（从 Nacos 查地址）→ 送快递（转发请求）。**
