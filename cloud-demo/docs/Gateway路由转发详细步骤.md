# Gateway 路由转发详细步骤

## 问题：http://localhost:10010/user/1 怎么转发到 http://localhost:8081/user/1？

---

## 一、配置文件

### Gateway 配置（application.yml）

```yaml
gateway:
  routes:
    - id: user-service                    # 路由 ID（自定义名字）
      uri: lb://userservice               # 目标服务（lb = 负载均衡）
      predicates:
        - Path=/user/**                   # 匹配规则（/user/ 开头的都匹配）
```

**配置解读：**
- `id: user-service`：这条路由的名字，随便起，只要唯一就行
- `uri: lb://userservice`：
  - `lb` = LoadBalance（负载均衡）
  - `userservice` = 服务名（要去 Nacos 查地址）
- `predicates: - Path=/user/**`：
  - 如果请求路径是 `/user/` 开头的，就用这条路由
  - `**` = 后面可以跟任意内容

---

## 二、完整转发流程（7步）

```
┌─────────────────────────────────────────────────────────────────┐
│  第 1 步：客户端发送请求                                         │
│                                                                 │
│  浏览器输入：http://localhost:10010/user/1                      │
│                                                                 │
│  分解：                                                          │
│    协议：http://                                                │
│    主机：localhost                                              │
│    端口：10010  ← Gateway 的端口                                │
│    路径：/user/1                                                │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ GET /user/1
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 2 步：Gateway 收到请求                                       │
│                                                                 │
│  Gateway (端口 10010) 收到：                                     │
│    方法：GET                                                    │
│    路径：/user/1                                                │
│                                                                 │
│  Gateway 想：这个请求要转发到哪里？我得看看路由配置              │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 3 步：匹配路由规则                                           │
│                                                                 │
│  Gateway 查看配置：                                              │
│    predicates:                                                  │
│      - Path=/user/**                                            │
│                                                                 │
│  Gateway 想：                                                    │
│    请求路径是：/user/1                                           │
│    匹配规则是：/user/**                                          │
│                                                                 │
│  开始匹配：                                                      │
│    1. /user/1 是不是以 /user/ 开头？                            │
│       → 是的 ✅                                                 │
│                                                                 │
│    2. /user/ 后面有内容吗？                                     │
│       → 有，是 "1" ✅                                           │
│                                                                 │
│    3. ** 表示后面可以跟任意内容                                 │
│       → /user/1 符合 /user/** ✅                                │
│                                                                 │
│  结论：匹配成功！                                                │
│                                                                 │
│  Gateway 决定：使用这条路由，目标是 lb://userservice             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 4 步：解析 lb://userservice                                  │
│                                                                 │
│  Gateway 看到：uri: lb://userservice                             │
│                                                                 │
│  Gateway 想：                                                    │
│    lb = LoadBalance（负载均衡）                                  │
│    userservice = 服务名                                          │
│                                                                 │
│  Gateway 知道：                                                  │
│    这不是一个固定地址（不是 http://localhost:8081）              │
│    这是一个服务名，我需要去 Nacos 查询它的真实地址               │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 查询服务地址
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 5 步：从 Nacos 查询服务地址                                  │
│                                                                 │
│  Gateway → Nacos：                                               │
│    请求：userservice 服务在哪里？                                │
│                                                                 │
│  Nacos 查注册表：                                                │
│    服务名：userservice                                           │
│    实例列表：                                                    │
│      - 实例 1：localhost:8081 ✅ 健康                           │
│                                                                 │
│  Nacos → Gateway：                                               │
│    响应：userservice 在 localhost:8081                           │
│                                                                 │
│  就像查电话簿：                                                  │
│    问：张三的电话是多少？                                        │
│    答：张三的电话是 138xxxx                                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 返回地址：localhost:8081
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 6 步：负载均衡（选择实例）                                    │
│                                                                 │
│  Gateway 收到实例列表：                                          │
│    - localhost:8081                                             │
│                                                                 │
│  Gateway 想：                                                    │
│    只有 1 个实例，就选它了                                       │
│                                                                 │
│  如果有多个实例（假设）：                                        │
│    - localhost:8081                                             │
│    - localhost:8082                                             │
│    - localhost:8083                                             │
│                                                                 │
│  Gateway 会轮流选择：                                            │
│    第 1 次请求 → 8081                                           │
│    第 2 次请求 → 8082                                           │
│    第 3 次请求 → 8083                                           │
│    第 4 次请求 → 8081                                           │
│    ...                                                          │
│                                                                 │
│  Gateway 决定：转发到 localhost:8081                             │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  第 7 步：转发请求                                               │
│                                                                 │
│  Gateway 构造新请求：                                            │
│    原始请求：http://localhost:10010/user/1                      │
│    目标地址：localhost:8081                                     │
│    新请求：http://localhost:8081/user/1                         │
│                                                                 │
│  变化：                                                          │
│    主机：localhost（不变）                                      │
│    端口：10010 → 8081（变了）                                   │
│    路径：/user/1（不变）                                        │
│                                                                 │
│  Gateway 发送：GET http://localhost:8081/user/1                 │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ 转发请求
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│  UserService 收到请求                                            │
│                                                                 │
│  UserService (端口 8081) 收到：                                  │
│    方法：GET                                                    │
│    路径：/user/1                                                │
│                                                                 │
│  UserController 处理：                                           │
│    @GetMapping("/{id}")                                         │
│    public User queryById(@PathVariable Long id) {              │
│        return userService.queryById(1);                         │
│    }                                                            │
│                                                                 │
│  查数据库：SELECT * FROM user WHERE id = 1                       │
│  返回数据：{"id":1,"username":"柳岩","address":"湖南"}           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、关键步骤详解

### 步骤 3：路径匹配详解

**匹配规则：`/user/**`**

`**` 是通配符，表示：
- `/user/` 后面可以跟**任意内容**
- 可以是一层路径，也可以是多层路径

**匹配示例：**

✅ 能匹配：
- `/user/1` → 后面是 `1`
- `/user/123` → 后面是 `123`
- `/user/abc` → 后面是 `abc`
- `/user/order/list` → 后面是 `order/list`（多层也行）
- `/user/a/b/c/d` → 后面是 `a/b/c/d`（多层也行）

❌ 不能匹配：
- `/order/1` → 开头不是 `/user/`
- `/user` → 后面没有 `/`
- `/userinfo/1` → 开头不是 `/user/`（是 `/userinfo/`）

**所以：**
- 请求路径：`/user/1`
- 匹配规则：`/user/**`
- 检查：`/user/1` 是不是以 `/user/` 开头？→ 是 ✅
- 检查：`/user/` 后面有内容吗？→ 有，是 `1` ✅
- 结论：**匹配成功！**

---

### 步骤 4：lb://userservice 详解

**uri: lb://userservice**

这是一个特殊的地址格式：

```
lb://userservice
│   │
│   └─ 服务名（要去 Nacos 查地址）
│
└─ LoadBalance（负载均衡）
```

**对比两种写法：**

#### 写法 1：固定地址

```yaml
uri: http://localhost:8081
```

- 直接写死地址
- Gateway 直接转发到 `http://localhost:8081`
- 不需要查 Nacos
- 缺点：地址变了，配置也要改

#### 写法 2：服务名（推荐）

```yaml
uri: lb://userservice
```

- 写服务名
- Gateway 去 Nacos 查询 `userservice` 的地址
- 地址变了，只需要在 Nacos 更新
- 支持负载均衡（多个实例轮流选）

---

### 步骤 5：Nacos 查询详解

**Gateway 怎么知道去 Nacos 查？**

因为 Gateway 的配置里有：

```yaml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848  # Nacos 地址
```

**查询过程：**

```
Gateway → Nacos：
  请求：GET /nacos/v1/ns/instance/list?serviceName=userservice
  
Nacos → Gateway：
  响应：
  {
    "hosts": [
      {
        "ip": "localhost",
        "port": 8081,
        "healthy": true
      }
    ]
  }
```

**就像查电话簿：**
- Gateway：userservice 的电话（地址）是多少？
- Nacos：userservice 的电话是 localhost:8081

---

### 步骤 7：请求转发详解

**原始请求：**
```
http://localhost:10010/user/1
│      │         │     │
│      │         │     └─ 路径
│      │         └─ 端口（Gateway）
│      └─ 主机
└─ 协议
```

**转发后：**
```
http://localhost:8081/user/1
│      │         │    │
│      │         │    └─ 路径（不变）
│      │         └─ 端口（UserService）
│      └─ 主机（不变）
└─ 协议（不变）
```

**变化：**
- 端口：`10010` → `8081`
- 其他都不变

---

## 四、完整流程图（简化版）

```
客户端
  │
  │ ① 发送：http://localhost:10010/user/1
  ↓
Gateway (10010)
  │
  │ ② 收到：GET /user/1
  │ ③ 匹配：/user/** ✅
  │ ④ 解析：lb://userservice
  │ ⑤ 查询 Nacos：userservice 在哪？
  │ ⑥ Nacos 返回：localhost:8081
  │ ⑦ 转发：http://localhost:8081/user/1
  ↓
UserService (8081)
  │
  │ ⑧ 处理请求
  │ ⑨ 返回数据
  ↓
Gateway
  │
  │ ⑩ 转发响应
  ↓
客户端
```

---

## 五、总结

### 核心流程（7步）

1. **客户端发送请求**：`http://localhost:10010/user/1`
2. **Gateway 收到请求**：`GET /user/1`
3. **匹配路由规则**：`/user/1` 符合 `/user/**` ✅
4. **解析目标地址**：`lb://userservice`（需要查 Nacos）
5. **查询 Nacos**：`userservice` 在 `localhost:8081`
6. **负载均衡**：选择 `localhost:8081`
7. **转发请求**：`http://localhost:8081/user/1`

### 关键点

- **路径匹配**：`/user/**` 表示 `/user/` 后面可以跟任意内容
- **lb://userservice**：`lb` = 负载均衡，`userservice` = 服务名
- **Nacos 查询**：Gateway 从 Nacos 查询服务的真实地址
- **请求转发**：端口从 `10010` 变成 `8081`，路径不变

### 一句话总结

**Gateway 收到请求 → 匹配路由规则 → 从 Nacos 查服务地址 → 转发请求到目标服务。**

---

## 六、实际测试

### 测试 1：直接访问 UserService

```bash
# 不通过 Gateway，直接访问 UserService
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

### 测试 3：查看日志

**Gateway 日志：**
```
收到请求：GET /user/1
匹配路由：user-service
目标服务：lb://userservice
查询 Nacos：userservice
Nacos 返回：localhost:8081
转发请求：http://localhost:8081/user/1
```

**UserService 日志：**
```
收到请求：GET /user/1
查询用户：id=1
返回数据：{"id":1,"username":"柳岩","address":"湖南省衡阳市"}
```

---

## 七、常见问题

### Q1：为什么要用 lb://userservice 而不是 http://localhost:8081？

**A：**
- `http://localhost:8081`：固定地址，地址变了配置也要改
- `lb://userservice`：服务名，地址变了只需要在 Nacos 更新
- `lb://userservice` 支持负载均衡，多个实例轮流选

### Q2：/user/** 中的 ** 是什么意思？

**A：**
- `**` 是通配符，表示"后面可以跟任意内容"
- `/user/**` 可以匹配：
  - `/user/1`
  - `/user/123`
  - `/user/a/b/c`
  - 等等

### Q3：Gateway 怎么知道去 Nacos 查？

**A：**
- Gateway 的配置里有 Nacos 地址：
  ```yaml
  spring:
    cloud:
      nacos:
        server-addr: localhost:8848
  ```
- 看到 `lb://` 就知道要去 Nacos 查

### Q4：如果 Nacos 里有多个 userservice 实例怎么办？

**A：**
- Gateway 会轮流选择（负载均衡）
- 例如：
  - 第 1 次请求 → 实例 1
  - 第 2 次请求 → 实例 2
  - 第 3 次请求 → 实例 3
  - 第 4 次请求 → 实例 1
  - ...

### Q5：路径会变吗？

**A：**
- 不会变
- 原始请求：`http://localhost:10010/user/1`
- 转发后：`http://localhost:8081/user/1`
- 路径都是 `/user/1`，只有端口变了
