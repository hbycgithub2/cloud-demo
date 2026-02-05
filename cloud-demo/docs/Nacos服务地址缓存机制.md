# Nacos 服务地址缓存机制详解

## 你的三个问题

### Q1: userservice 是 Feign 接口的服务名吗？
**A: 是的！**

```java
@FeignClient("userservice")  // ← 这就是服务名
public interface UserClient {
    @GetMapping("/user/{id}")
    User findById(@PathVariable("id") Long id);
}
```

### Q2: 缓存是什么？
**A: 本地内存里的一个 Map，存储服务名和地址列表的映射**

```java
// 简化版缓存结构
Map<String, List<String>> serviceCache = {
    "userservice": ["192.168.26.1:8081", "192.168.26.2:8081"],
    "orderservice": ["192.168.26.1:8080"]
}
```

### Q3: 缓存是怎么存进去的？
**A: OrderService 启动时，自动从 Nacos 拉取并定时更新**

---

## 完整流程：缓存是怎么来的

### 第一步：OrderService 启动

```
1. OrderService 启动
   ↓
2. 发现配置了 @FeignClient("userservice")
   ↓
3. 自动创建 NacosNamingService（Nacos 客户端）
   ↓
4. 订阅 userservice 的服务列表
```

---

### 第二步：订阅服务（自动拉取）

```java
// NacosNamingService.java（Nacos 客户端）
public class NacosNamingService {
    
    // 本地缓存
    private Map<String, List<Instance>> serviceCache = new ConcurrentHashMap<>();
    
    // OrderService 启动时自动调用
    public void subscribe(String serviceName) {
        // 1. 发送 HTTP 请求到 Nacos
        List<Instance> instances = queryInstancesFromNacos(serviceName);
        
        // 2. 存到本地缓存
        serviceCache.put(serviceName, instances);
        
        // 3. 启动定时任务，每 10 秒更新一次
        startUpdateTask(serviceName);
        
        // 4. 监听 Nacos 的推送（有变化立即通知）
        listenToNacosPush(serviceName);
    }
    
    // 查询 Nacos
    private List<Instance> queryInstancesFromNacos(String serviceName) {
        // 发送 HTTP 请求
        String url = "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=" + serviceName;
        String response = httpClient.get(url);
        
        // 解析响应
        return parseInstances(response);
    }
}
```

---

### 第三步：Nacos 返回服务列表

**HTTP 请求：**
```
GET http://localhost:8848/nacos/v1/ns/instance/list?serviceName=userservice
```

**Nacos 响应：**
```json
{
  "hosts": [
    {
      "ip": "192.168.26.1",
      "port": 8081,
      "healthy": true,
      "weight": 1.0,
      "clusterName": "SH"
    },
    {
      "ip": "192.168.26.2",
      "port": 8081,
      "healthy": true,
      "weight": 1.0,
      "clusterName": "SH"
    }
  ]
}
```

---

### 第四步：存入本地缓存

```java
// 解析后存入缓存
serviceCache.put("userservice", [
    new Instance("192.168.26.1", 8081, true),
    new Instance("192.168.26.2", 8081, true)
]);
```

**缓存结构：**
```
OrderService 内存中：
├── serviceCache (Map)
    ├── "userservice" → [192.168.26.1:8081, 192.168.26.2:8081]
    └── "orderservice" → [192.168.26.1:8080]
```

---

### 第五步：定时更新缓存

```java
// 每 10 秒更新一次
private void startUpdateTask(String serviceName) {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    
    executor.scheduleAtFixedRate(() -> {
        // 1. 从 Nacos 查询最新的服务列表
        List<Instance> instances = queryInstancesFromNacos(serviceName);
        
        // 2. 更新本地缓存
        serviceCache.put(serviceName, instances);
        
        System.out.println("更新缓存：" + serviceName + " -> " + instances);
    }, 0, 10, TimeUnit.SECONDS);
}
```

---

### 第六步：Nacos 主动推送（UDP）

除了定时拉取，Nacos 还会主动推送变化：

```java
// 监听 Nacos 的 UDP 推送
private void listenToNacosPush(String serviceName) {
    // 开启 UDP 端口监听
    DatagramSocket socket = new DatagramSocket();
    
    new Thread(() -> {
        while (true) {
            // 接收 Nacos 的推送
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            
            // 解析推送内容
            String message = new String(packet.getData());
            if (message.contains(serviceName)) {
                // 服务列表有变化，立即更新缓存
                List<Instance> instances = queryInstancesFromNacos(serviceName);
                serviceCache.put(serviceName, instances);
                
                System.out.println("收到 Nacos 推送，更新缓存：" + serviceName);
            }
        }
    }).start();
}
```

---

## 完整时间线

### OrderService 启动时（第 0 秒）

```
1. OrderService 启动
2. 扫描到 @FeignClient("userservice")
3. 创建 NacosNamingService
4. 订阅 userservice
5. 发送 HTTP 请求到 Nacos：
   GET /nacos/v1/ns/instance/list?serviceName=userservice
6. Nacos 返回：
   [192.168.26.1:8081, 192.168.26.2:8081]
7. 存入本地缓存：
   serviceCache.put("userservice", [...])
8. 启动定时任务（每 10 秒更新）
9. 启动 UDP 监听（接收 Nacos 推送）
```

### 第 10 秒（定时更新）

```
1. 定时任务触发
2. 再次查询 Nacos
3. 更新本地缓存
```

### 第 15 秒（有新实例上线）

```
1. 新的 UserService 实例启动：192.168.26.3:8081
2. 注册到 Nacos
3. Nacos 通过 UDP 推送给 OrderService：
   "userservice 有新实例上线"
4. OrderService 立即查询 Nacos
5. 更新本地缓存：
   [192.168.26.1:8081, 192.168.26.2:8081, 192.168.26.3:8081]
```

### 第 30 秒（用户发起请求）

```
1. 用户访问：GET /order/101
2. OrderService 调用 userClient.findById(1)
3. Feign 查询本地缓存：
   serviceCache.get("userservice")
4. 得到：[192.168.26.1:8081, 192.168.26.2:8081, 192.168.26.3:8081]
5. Ribbon 负载均衡选一个：192.168.26.2:8081
6. 发送请求：GET http://192.168.26.2:8081/user/1
```

---

## 缓存更新机制

### 1. 定时拉取（Pull）
```
每 10 秒主动查询 Nacos
优点：可靠
缺点：有延迟（最多 10 秒）
```

### 2. 主动推送（Push）
```
Nacos 通过 UDP 推送变化
优点：实时（秒级）
缺点：UDP 可能丢包
```

### 3. 双重保障
```
定时拉取 + 主动推送 = 既实时又可靠
```

---

## 缓存的生命周期

### 创建缓存
```java
// OrderService 启动时
@PostConstruct
public void init() {
    // 自动订阅 userservice
    nacosNamingService.subscribe("userservice");
}
```

### 更新缓存
```java
// 方式 1：定时更新（每 10 秒）
scheduleUpdate("userservice", 10, TimeUnit.SECONDS);

// 方式 2：收到 Nacos 推送
onNacosPush("userservice", newInstances);
```

### 使用缓存
```java
// Feign 调用时
List<Instance> instances = serviceCache.get("userservice");
Instance selected = loadBalancer.choose(instances);
String url = "http://" + selected.getIp() + ":" + selected.getPort();
```

### 清除缓存
```java
// OrderService 关闭时
@PreDestroy
public void destroy() {
    // 取消订阅
    nacosNamingService.unsubscribe("userservice");
    // 清空缓存
    serviceCache.clear();
}
```

---

## 实际代码示例

### 查看缓存内容

```java
@RestController
public class DebugController {
    
    @Autowired
    private NacosNamingService namingService;
    
    @GetMapping("/debug/cache")
    public Map<String, List<Instance>> getCache() {
        // 查看当前缓存的所有服务
        return namingService.getServiceCache();
    }
}
```

**访问：**
```bash
curl http://localhost:8080/debug/cache
```

**返回：**
```json
{
  "userservice": [
    {"ip": "192.168.26.1", "port": 8081, "healthy": true},
    {"ip": "192.168.26.2", "port": 8081, "healthy": true}
  ]
}
```

---

## 缓存的好处

### 1. 性能优化
```
不用每次调用都查 Nacos
直接从本地内存读取（毫秒级）
```

### 2. 容错能力
```
即使 Nacos 挂了，缓存还在
短时间内服务调用不受影响
```

### 3. 减轻 Nacos 压力
```
1000 个服务每秒调用 100 次
如果每次都查 Nacos = 100,000 QPS
用缓存 = 只需要定时更新（10 秒一次）
```

---

## 缓存的风险

### 1. 延迟问题
```
服务下线了，但缓存还没更新
可能会调用到已下线的服务
解决：Nacos 推送 + 定时更新 + 重试机制
```

### 2. 内存占用
```
服务很多时，缓存会占用内存
解决：只缓存需要调用的服务
```

---

## 总结

### userservice 是什么？
```
@FeignClient("userservice")  ← 这就是服务名
```

### 缓存是什么？
```java
Map<String, List<Instance>> serviceCache = {
    "userservice": [
        {ip: "192.168.26.1", port: 8081},
        {ip: "192.168.26.2", port: 8081}
    ]
}
```

### 缓存怎么来的？
```
1. OrderService 启动
2. 发现 @FeignClient("userservice")
3. 自动订阅 userservice
4. 从 Nacos 查询服务列表
5. 存入本地缓存
6. 定时更新（每 10 秒）
7. 监听 Nacos 推送（实时更新）
```

### 缓存怎么用的？
```
1. Feign 调用 userClient.findById()
2. 从缓存查询 userservice 的地址
3. Ribbon 负载均衡选一个
4. 发送 HTTP 请求
```

**核心：缓存是自动管理的，你不用写任何代码！**
