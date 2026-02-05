# OrderService 服务说明

## 一、OrderService 是干啥的

**查询订单详情，顺便把下单用户的信息也查出来。**

---

## 二、怎么访问

### 接口地址
```
http://localhost:8080/order/101
```

### 返回结果
```json
{
  "id": 101,
  "name": "Apple 苹果 iPhone 12",
  "price": 699900,
  "num": 1,
  "userId": 1,
  "user": {
    "id": 1,
    "username": "柳岩",
    "address": "湖南长沙"
  }
}
```

**说人话：** 查订单 101，返回订单信息 + 下单用户（柳岩）的信息。

---

## 三、和 UserService (8081) 啥关系

### 简单说

**OrderService 要查用户信息，自己没有，就去问 UserService 要。**

---

### 详细流程

```
1. 你访问：http://localhost:8080/order/101

2. OrderService 干了两件事：
   ① 查自己的数据库（cloud_order）
      → 查到订单：id=101, userId=1, 商品=iPhone 12
   
   ② 发现订单是用户 1 下的，但不知道用户 1 是谁
      → 去问 UserService：http://localhost:8081/user/1
      → UserService 返回：{"id":1, "username":"柳岩", "address":"湖南长沙"}
   
   ③ 把订单信息 + 用户信息拼起来
      → 返回给你

3. 你收到完整的订单信息（包含用户信息）
```

---

## 四、访问路径（画个图）

```
你的浏览器
    ↓
http://localhost:8080/order/101
    ↓
OrderService (8080端口)
    ↓
查订单表：SELECT * FROM tb_order WHERE id = 101
    ↓
得到：订单 101 是用户 1 下的
    ↓
调用 UserService：http://localhost:8081/user/1
    ↓
UserService (8081端口)
    ↓
查用户表：SELECT * FROM tb_user WHERE id = 1
    ↓
返回：{"id":1, "username":"柳岩"}
    ↓
OrderService 收到用户信息
    ↓
拼装：订单 + 用户
    ↓
返回给你
```

---

## 五、为啥要这么搞

### 问题
订单表只存了 `user_id = 1`，但你想知道用户叫啥、住哪。

### 两种方案

#### 方案1：订单表存所有用户信息（不推荐）
```sql
CREATE TABLE tb_order (
  id BIGINT,
  user_id BIGINT,
  username VARCHAR(100),  -- 冗余
  address VARCHAR(200)    -- 冗余
);
```
**问题：** 用户改地址，所有订单都要改，数据不一致。

#### 方案2：订单服务调用用户服务（推荐）
```
订单表只存 user_id
需要用户信息时，实时去用户服务查
```
**好处：** 数据不冗余，用户信息永远是最新的。

---

## 六、技术实现

### 代码怎么写的

```java
@Service
public class OrderService {
    
    @Autowired
    private UserClient userClient;  // Feign 客户端
    
    public Order queryOrderById(Long orderId) {
        // 1. 查订单
        Order order = orderMapper.findById(101);
        
        // 2. 调用用户服务（像调本地方法一样简单）
        User user = userClient.findById(order.getUserId());
        
        // 3. 拼装
        order.setUser(user);
        
        return order;
    }
}
```

### Feign 是啥

**Feign = 远程调用工具，让调用其他服务像调本地方法一样简单。**

```java
// 定义接口
@FeignClient("userservice")  // 服务名
public interface UserClient {
    @GetMapping("/user/{id}")
    User findById(@PathVariable("id") Long id);
}

// 使用（就像调本地方法）
User user = userClient.findById(1);

// Feign 自动帮你：
// 1. 去 Nacos 查 userservice 的地址（localhost:8081）
// 2. 发 HTTP 请求：GET http://localhost:8081/user/1
// 3. 把返回的 JSON 转成 User 对象
```

---

### Feign 调用详细流程

#### 第1步：你写代码调用
```java
User user = userClient.findById(1);
```

#### 第2步：Feign 拦截这个调用
```
Feign 发现：
- 接口上有 @FeignClient("userservice")
- 方法上有 @GetMapping("/user/{id}")
- 参数是 id=1

Feign 知道要做什么：
- 调用服务名为 "userservice" 的服务
- 请求路径是 /user/1
- 使用 GET 方法
```

#### 第3步：Feign 问 Nacos 要地址
```
Feign: "Nacos，userservice 服务在哪？"

Nacos: "我这里注册了 2 个 userservice 实例：
  - 实例1：192.168.1.100:8081 (集群 SH)
  - 实例2：192.168.1.101:8081 (集群 SH)"

Feign: "好的，我选实例1"
```

**Nacos 怎么知道的？**
```
UserService 启动时：
  ↓
读取配置 application.yml
  spring.application.name: userservice
  spring.cloud.nacos.server-addr: localhost:8848
  ↓
连接 Nacos Server (localhost:8848)
  ↓
发送注册请求
  POST http://localhost:8848/nacos/v1/ns/instance
  参数：
    serviceName: userservice
    ip: 192.168.1.100
    port: 8081
    clusterName: SH
  ↓
Nacos 记录：
  服务名: userservice
  实例列表:
    - 192.168.1.100:8081 (SH集群)
    - 192.168.1.101:8081 (SH集群)
```

#### 第4步：Feign 发 HTTP 请求
```
Feign 构建请求：
  GET http://192.168.1.100:8081/user/1
  
发送请求 → UserService 收到 → 查数据库 → 返回 JSON
```

#### 第5步：Feign 把 JSON 转成对象
```
收到响应：
{"id":1, "username":"柳岩", "address":"湖南长沙"}

Feign 自动转换：
User user = new User();
user.setId(1);
user.setUsername("柳岩");
user.setAddress("湖南长沙");

返回给你
```

---

### Nacos 在这扮演啥角色

**Nacos = 服务注册中心 + 通讯录**

#### 比喻说明

```
没有 Nacos 的情况：
OrderService: "我要调用 UserService，地址是多少？"
你: "写死在代码里：http://192.168.1.100:8081"

问题：
- UserService 换机器了，代码要改
- UserService 加机器了，代码要改
- UserService 挂了，不知道
```

```
有 Nacos 的情况：
OrderService: "我要调用 userservice，地址是多少？"
Nacos: "我这有 2 个实例，给你：
  - 192.168.1.100:8081 (健康)
  - 192.168.1.101:8081 (健康)"

好处：
- UserService 换机器，自动更新
- UserService 加机器，自动发现
- UserService 挂了，自动剔除
```

---

### Nacos 和 Feign 的关系

```
Feign (远程调用工具)
    ↓
需要知道服务地址
    ↓
问 Nacos (服务注册中心)
    ↓
Nacos 返回地址列表
    ↓
Feign 选一个地址
    ↓
发 HTTP 请求
```

**说人话：**
- **Feign**：负责发请求（打电话的人）
- **Nacos**：负责提供地址（通讯录）

---

### 完整调用流程图

```
OrderService 启动
    ↓
连接 Nacos，注册自己
    serviceName: orderservice
    ip: 192.168.1.100
    port: 8080
    ↓
Nacos 记录：orderservice → 192.168.1.100:8080

UserService 启动
    ↓
连接 Nacos，注册自己
    serviceName: userservice
    ip: 192.168.1.101
    port: 8081
    ↓
Nacos 记录：userservice → 192.168.1.101:8081

---

用户访问：http://localhost:8080/order/101
    ↓
OrderService 处理
    ↓
需要调用 UserService
    ↓
执行：userClient.findById(1)
    ↓
Feign 拦截
    ↓
Feign 问 Nacos："userservice 在哪？"
    ↓
Nacos 查询注册表
    ↓
Nacos 返回：[192.168.1.101:8081]
    ↓
Feign 发请求：GET http://192.168.1.101:8081/user/1
    ↓
UserService 处理
    ↓
返回：{"id":1, "username":"柳岩"}
    ↓
Feign 转成对象：User
    ↓
OrderService 收到 User 对象
    ↓
拼装订单 + 用户
    ↓
返回给用户
```

---

### Nacos 的核心功能

#### 1. 服务注册
```
服务启动时，向 Nacos 报告：
"我是 userservice，地址是 192.168.1.100:8081"

Nacos 记录到注册表
```

#### 2. 服务发现
```
OrderService 问 Nacos：
"userservice 在哪？"

Nacos 返回：
"192.168.1.100:8081 和 192.168.1.101:8081"
```

#### 3. 健康检查
```
Nacos 每 5 秒检查一次：
"userservice 还活着吗？"

如果 3 次没响应：
"标记为不健康，不再推荐"
```

#### 4. 负载均衡
```
Nacos 返回多个实例：
- 192.168.1.100:8081
- 192.168.1.101:8081

Feign 选一个（轮询/随机/权重）
```

---

### 代码中的体现

#### UserService 注册到 Nacos
```yaml
# application.yml
spring:
  application:
    name: userservice  # 服务名
  cloud:
    nacos:
      server-addr: localhost:8848  # Nacos 地址
      discovery:
        cluster-name: SH  # 集群名
```

```java
// 启动类（自动注册）
@SpringBootApplication
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
        // Spring Boot 自动连接 Nacos 并注册
    }
}
```

#### OrderService 从 Nacos 发现服务
```java
// Feign 接口
@FeignClient("userservice")  // 服务名，不是 IP
public interface UserClient {
    @GetMapping("/user/{id}")
    User findById(@PathVariable("id") Long id);
}

// 使用
@Service
public class OrderService {
    @Autowired
    private UserClient userClient;
    
    public Order queryOrderById(Long orderId) {
        // Feign 自动去 Nacos 查 userservice 的地址
        User user = userClient.findById(1);
        return order;
    }
}
```

---

### Nacos 控制台查看

访问：`http://localhost:8848/nacos`

**服务列表：**
```
服务名          实例数    健康实例
orderservice    1         1
userservice     2         2
```

**点击 userservice 查看详情：**
```
实例列表：
IP              端口    集群    健康状态
192.168.1.100   8081    SH      健康
192.168.1.101   8081    SH      健康
```

---

### 总结

#### Feign 调用流程（5步）
1. **你写代码**：`userClient.findById(1)`
2. **Feign 拦截**：解析 `@FeignClient("userservice")`
3. **查询 Nacos**：问 "userservice 在哪？"
4. **发送请求**：`GET http://192.168.1.100:8081/user/1`
5. **转换结果**：JSON → User 对象

#### Nacos 的角色
- **服务注册中心**：记录所有服务的地址
- **通讯录**：告诉 Feign 服务在哪
- **健康检查**：剔除挂掉的服务
- **负载均衡**：返回多个实例供选择

#### 关键点
- **服务名**：`userservice`（不是 IP 地址）
- **自动注册**：服务启动时自动注册到 Nacos
- **自动发现**：Feign 自动从 Nacos 查地址
- **动态更新**：服务上线/下线，Nacos 实时更新

#### 一句话总结
**Feign 负责发请求，Nacos 负责告诉 Feign 服务在哪，两者配合实现服务间调用。**

---

## 七、服务关系

```
OrderService (8080)  ←→  UserService (8081)
    ↓                         ↓
cloud_order 数据库      cloud_user 数据库
    ↓                         ↓
tb_order 表              tb_user 表
(存订单信息)             (存用户信息)
```

**说人话：**
- OrderService 管订单
- UserService 管用户
- 需要用户信息时，OrderService 问 UserService 要

---

## 八、实际例子

### 场景：查询订单 101

#### 步骤1：访问订单服务
```bash
curl http://localhost:8080/order/101
```

#### 步骤2：OrderService 查订单表
```sql
SELECT * FROM tb_order WHERE id = 101;
```
结果：
```
id=101, user_id=1, name="iPhone 12", price=699900
```

#### 步骤3：OrderService 调用 UserService
```
GET http://localhost:8081/user/1
```

#### 步骤4：UserService 查用户表
```sql
SELECT * FROM tb_user WHERE id = 1;
```
结果：
```
id=1, username="柳岩", address="湖南长沙"
```

#### 步骤5：UserService 返回
```json
{"id":1, "username":"柳岩", "address":"湖南长沙"}
```

#### 步骤6：OrderService 拼装返回
```json
{
  "id": 101,
  "name": "iPhone 12",
  "price": 699900,
  "user": {
    "id": 1,
    "username": "柳岩",
    "address": "湖南长沙"
  }
}
```

---

## 九、可以访问的接口

### 查询订单
```
GET http://localhost:8080/order/101
GET http://localhost:8080/order/102
GET http://localhost:8080/order/103
```

**说明：** 订单 ID 是多少，就查哪个订单。

---

## 十、总结

### 一句话说清楚

**OrderService 查订单，发现需要用户信息，就通过 Feign 调用 UserService 去查，最后把订单 + 用户信息一起返回。**

### 关键点

1. **OrderService 端口**：8080
2. **UserService 端口**：8081
3. **访问地址**：`http://localhost:8080/order/{订单ID}`
4. **调用关系**：OrderService → UserService
5. **技术**：Feign（远程调用）+ Nacos（服务发现）

### 为啥要分两个服务

- **解耦**：订单和用户各管各的
- **复用**：用户信息可以给订单、商品、评论等多个服务用
- **扩展**：订单服务压力大，可以单独加机器
- **维护**：改用户逻辑不影响订单服务

---

## 十一、配置文件

### OrderService 配置（8080端口）
```yaml
server:
  port: 8080

spring:
  application:
    name: orderservice
  datasource:
    url: jdbc:mysql://localhost:3306/cloud_order
    username: root
    password: root
  cloud:
    nacos:
      server-addr: localhost:8848
```

### UserService 配置（8081端口）
```yaml
server:
  port: 8081

spring:
  application:
    name: userservice
  datasource:
    url: jdbc:mysql://localhost:3306/cloud_user
    username: root
    password: root
  cloud:
    nacos:
      server-addr: localhost:8848
```

---

## 十二、常见问题

### 1. 为啥不直接查用户表？

**答：** 订单服务连的是 `cloud_order` 数据库，用户表在 `cloud_user` 数据库，查不到。

### 2. 为啥不把两个表放一个库？

**答：** 微服务架构，每个服务管自己的数据库，互不干扰。

### 3. 调用失败怎么办？

**答：** UserService 挂了，OrderService 会报错。可以加熔断降级（Sentinel），返回默认用户信息。

### 4. 性能会不会慢？

**答：** 多了一次网络调用，会慢一点（几毫秒），但换来了解耦和可扩展性，值得。

---

## 十三、相关文件

- OrderController: `order-service/src/main/java/cn/itcast/order/web/OrderController.java`
- OrderService: `order-service/src/main/java/cn/itcast/order/service/OrderService.java`
- UserClient: `feign-api/src/main/java/cn/itcast/feign/clients/UserClient.java`
- 配置文件: `order-service/src/main/resources/application.yml`
