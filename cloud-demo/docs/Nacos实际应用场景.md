# Nacos 在项目中的实际应用场景

## 场景一：服务发现 - 订单服务调用用户服务

### 业务场景
用户查询订单详情时，需要同时显示用户信息。订单服务需要调用用户服务获取用户数据。

### 传统方式的问题
```java
// ❌ 硬编码 IP 和端口，服务器换了就挂了
String url = "http://192.168.26.1:8081/user/" + userId;
User user = restTemplate.getForObject(url, User.class);
```

### 使用 Nacos 的方式

#### 1. 定义 Feign 客户端
```java
// UserClient.java
@FeignClient("userservice")  // ← 只写服务名，不写 IP 和端口！
public interface UserClient {
    
    @GetMapping("/user/{id}")
    User findById(@PathVariable("id") Long id);
}
```

#### 2. 订单服务直接调用
```java
// OrderService.java
@Service
public class OrderService {
    
    @Autowired
    private UserClient userClient;  // 注入 Feign 客户端
    
    public Order queryOrderById(Long orderId) {
        // 1. 查询订单
        Order order = orderMapper.findById(orderId);
        
        // 2. 调用用户服务（Nacos 自动找到 userservice 的地址）
        User user = userClient.findById(order.getUserId());
        
        // 3. 封装返回
        order.setUser(user);
        return order;
    }
}
```

#### 3. 控制器暴露接口
```java
// OrderController.java
@RestController
@RequestMapping("order")
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    @GetMapping("{orderId}")
    public Order queryOrderByUserId(@PathVariable("orderId") Long orderId) {
        return orderService.queryOrderById(orderId);
    }
}
```

### 背后发生了什么？

```
1. 用户访问：GET http://localhost:8080/order/101
   ↓
2. OrderService 需要查用户信息
   ↓
3. 调用 userClient.findById(userId)
   ↓
4. Feign 问 Nacos："userservice 在哪？"
   ↓
5. Nacos 回答："有 3 个实例：
   - 192.168.26.1:8081
   - 192.168.26.1:8082
   - 192.168.26.1:8083"
   ↓
6. Ribbon 负载均衡选一个：192.168.26.1:8081
   ↓
7. 发送请求：GET http://192.168.26.1:8081/user/1
   ↓
8. 拿到用户数据，返回给前端
```

### 好处
- ✅ 不用写死 IP 和端口
- ✅ 服务器扩容/缩容自动感知
- ✅ 自动负载均衡
- ✅ 服务挂了自动剔除

---

## 场景二：配置管理 - 动态修改配置不重启

### 业务场景
需要修改日期格式，传统方式要改配置文件、重新打包、重启服务。用 Nacos 可以在线修改，立即生效。

### 1. 配置类
```java
// PatternProperties.java
@Data
@Component
@ConfigurationProperties(prefix = "pattern")
public class PatternProperties {
    private String dateformat;      // 日期格式
    private String envSharedValue;  // 环境共享配置
    private String name;            // 环境名称
}
```

### 2. 使用配置
```java
// UserController.java
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private PatternProperties properties;
    
    // 查看当前配置
    @GetMapping("prop")
    public PatternProperties properties() {
        return properties;
    }
    
    // 使用配置格式化日期
    @GetMapping("now")
    public String now() {
        return LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern(properties.getDateformat()));
    }
}
```

### 3. Nacos 配置中心

在 Nacos 控制台创建配置：

**配置 ID**: `userservice-dev.yaml`

```yaml
pattern:
  dateformat: yyyy-MM-dd HH:mm:ss  # 日期格式
  name: dev环境
```

### 4. 动态修改

```
1. 访问：GET http://localhost:8081/user/now
   返回：2024-01-22 10:30:15
   
2. 在 Nacos 控制台修改配置：
   dateformat: MM月dd日 HH:mm
   
3. 再次访问：GET http://localhost:8081/user/now
   返回：01月22日 10:30
   
不用重启服务！立即生效！
```

### 配置文件
```yaml
# bootstrap.yml
spring:
  application:
    name: userservice
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        enabled: true              # 启用配置中心
        file-extension: yaml       # 配置文件格式
        namespace: 49dec1bd-xxx    # 命名空间（隔离环境）
```

### 好处
- ✅ 不用重启服务
- ✅ 多环境配置管理（dev/test/prod）
- ✅ 配置共享（多个服务用同一份配置）
- ✅ 配置历史版本管理
- ✅ 灰度发布（部分服务先用新配置）

---

## 场景三：集群管理 - 同城优先调用

### 业务场景
公司在杭州和上海都有机房，希望杭州的订单服务优先调用杭州的用户服务，减少跨城调用延迟。

### 配置集群

**OrderService（杭州）**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        cluster-name: HZ  # 杭州集群
```

**UserService（上海）**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        cluster-name: SH  # 上海集群
```

**UserService（杭州）**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        cluster-name: HZ  # 杭州集群
```

### 负载均衡策略
```yaml
# OrderService 配置
userservice:
  ribbon:
    NFLoadBalancerRuleClassName: com.alibaba.cloud.nacos.ribbon.NacosRule
```

### 调用优先级
```
杭州的 OrderService 调用 UserService：
1. 优先调用 HZ 集群的 UserService（同城）
2. HZ 集群都挂了，才调用 SH 集群（跨城）
```

---

## 场景四：健康检查 - 自动剔除故障服务

### 临时实例（默认）
```yaml
spring:
  cloud:
    nacos:
      discovery:
        ephemeral: true  # 临时实例
```

**工作原理：**
```
1. UserService 启动，注册到 Nacos
2. 每 5 秒发送心跳："我还活着"
3. Nacos 15 秒没收到心跳 → 标记为不健康
4. Nacos 30 秒没收到心跳 → 从列表中删除
5. OrderService 调用时，自动跳过不健康的实例
```

### 实际效果
```
场景：3 个 UserService 实例
- 192.168.26.1:8081 ✅ 健康
- 192.168.26.1:8082 ✅ 健康
- 192.168.26.1:8083 ❌ 挂了（没心跳）

OrderService 调用时：
- 只会调用 8081 和 8082
- 8083 自动被跳过
- 8083 恢复后，自动加回来
```

---

## 场景五：权重配置 - 灰度发布

### 业务场景
新版本 UserService 上线，先让 10% 的流量访问新版本，没问题再全量发布。

### Nacos 控制台配置权重

```
userservice 实例列表：
- 192.168.26.1:8081 (旧版本) 权重: 9
- 192.168.26.1:8082 (旧版本) 权重: 9
- 192.168.26.1:8083 (新版本) 权重: 1

流量分配：
- 8081: 45%
- 8082: 45%
- 8083: 10%  ← 新版本只接收 10% 流量
```

### 逐步放量
```
第一天：新版本权重 1（10% 流量）
第二天：新版本权重 3（30% 流量）
第三天：新版本权重 5（50% 流量）
第四天：新版本权重 10（100% 流量，下线旧版本）
```

---

## 场景六：命名空间 - 环境隔离

### 配置不同环境

**开发环境**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        namespace: dev-namespace-id
```

**测试环境**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        namespace: test-namespace-id
```

**生产环境**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        namespace: prod-namespace-id
```

### 隔离效果
```
开发环境的 OrderService 只能看到开发环境的 UserService
测试环境的 OrderService 只能看到测试环境的 UserService
生产环境的 OrderService 只能看到生产环境的 UserService

互不干扰！
```

---

## 总结：Nacos 在项目中的 6 大实际应用

| 场景 | 功能 | 解决的问题 |
|------|------|-----------|
| **服务发现** | 通过服务名调用 | 不用写死 IP，自动负载均衡 |
| **配置管理** | 动态修改配置 | 不用重启服务，多环境管理 |
| **集群管理** | 同城优先调用 | 减少跨城延迟，提高性能 |
| **健康检查** | 自动剔除故障 | 提高可用性，自动容错 |
| **权重配置** | 灰度发布 | 平滑上线，降低风险 |
| **命名空间** | 环境隔离 | dev/test/prod 互不干扰 |

---

## 快速测试

### 1. 启动服务
```bash
# 启动 Nacos
startup.cmd -m standalone

# 启动 UserService（8081）
java -jar user-service.jar

# 启动 OrderService（8080）
java -jar order-service.jar
```

### 2. 测试服务调用
```bash
# 查询订单（会自动调用用户服务）
curl http://localhost:8080/order/101
```

### 3. 测试配置管理
```bash
# 查看当前配置
curl http://localhost:8081/user/prop

# 在 Nacos 控制台修改配置后再查看
curl http://localhost:8081/user/prop
```

### 4. 查看 Nacos 控制台
```
http://localhost:8848/nacos
用户名：nacos
密码：nacos
```

就这么简单！
