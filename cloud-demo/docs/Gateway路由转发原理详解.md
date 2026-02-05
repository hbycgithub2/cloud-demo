# Gateway 路由转发原理详解

## 一、路由转发概述

Gateway 路由转发是指：**根据请求的特征（路径、请求头、参数等），将请求转发到对应的目标服务**。

**核心问题：**
1. 如何匹配路由？
2. 如何找到目标服务？
3. 如何转发请求？
4. 如何处理响应？

## 二、路由转发完整流程

### 2.1 流程图

```
客户端请求
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 1. Gateway 接收请求                                          │
│    ServerWebExchange (请求上下文)                            │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. 执行 GlobalFilter 链（前置处理）                          │
│    ├─ AuthorizeFilter (鉴权)                                │
│    ├─ LoggingFilter (日志)                                  │
│    └─ ... (其他全局过滤器)                                   │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 路由匹配 (RoutePredicateHandlerMapping)                  │
│    遍历所有路由，检查 Predicates 是否匹配                     │
│    ├─ Path 断言：检查路径                                    │
│    ├─ Method 断言：检查 HTTP 方法                            │
│    ├─ Header 断言：检查请求头                                │
│    └─ ... (其他断言)                                         │
│    → 找到匹配的路由                                          │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 解析目标 URI                                              │
│    ├─ lb://userservice → 从 Nacos 获取服务实例列表           │
│    └─ http://localhost:8081 → 直接使用固定地址               │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 负载均衡 (LoadBalancerClientFilter)                       │
│    从服务实例列表中选择一个实例                                │
│    ├─ 轮询策略 (RoundRobin)                                  │
│    ├─ 随机策略 (Random)                                      │
│    └─ 权重策略 (Weight)                                      │
│    → 选中：192.168.1.100:8081                                │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. 执行路由过滤器 (GatewayFilter)                            │
│    ├─ AddRequestHeader (添加请求头)                          │
│    ├─ StripPrefix (去除路径前缀)                             │
│    ├─ RewritePath (重写路径)                                 │
│    └─ ... (其他路由过滤器)                                   │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. 发送 HTTP 请求 (NettyRoutingFilter)                       │
│    使用 Netty HttpClient 发送请求到目标服务                   │
│    → http://192.168.1.100:8081/user/1                       │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 8. 目标服务处理请求                                          │
│    UserService 处理业务逻辑并返回响应                         │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 9. Gateway 接收响应                                          │
│    NettyWriteResponseFilter 处理响应                         │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 10. 执行 GlobalFilter 链（后置处理）                         │
│     处理响应，添加响应头等                                    │
└─────────────────────────────────────────────────────────────┘
    ↓
返回给客户端
```

## 三、核心组件详解

### 3.1 ServerWebExchange（请求上下文）

```java
public interface ServerWebExchange {
    ServerHttpRequest getRequest();   // 获取请求对象
    ServerHttpResponse getResponse(); // 获取响应对象
    Map<String, Object> getAttributes(); // 存储请求属性
}
```

**作用：** 封装请求和响应，在整个过滤器链中传递

**示例：**
```java
// 获取请求信息
ServerHttpRequest request = exchange.getRequest();
String path = request.getPath().value();  // /user/1
String method = request.getMethodValue(); // GET
MultiValueMap<String, String> params = request.getQueryParams();

// 存储属性（在过滤器间传递数据）
exchange.getAttributes().put("startTime", System.currentTimeMillis());
```

### 3.2 RouteDefinition（路由定义）

```java
public class RouteDefinition {
    private String id;                    // 路由 ID
    private URI uri;                      // 目标 URI
    private List<PredicateDefinition> predicates;  // 断言列表
    private List<FilterDefinition> filters;        // 过滤器列表
    private int order = 0;                // 优先级
}
```

**配置示例：**
```yaml
routes:
  - id: user-service
    uri: lb://userservice
    predicates:
      - Path=/user/**
    filters:
      - AddRequestHeader=X-Request-Id, 123
```

**对应的 RouteDefinition：**
```java
RouteDefinition route = new RouteDefinition();
route.setId("user-service");
route.setUri(URI.create("lb://userservice"));

PredicateDefinition predicate = new PredicateDefinition();
predicate.setName("Path");
predicate.addArg("pattern", "/user/**");
route.setPredicates(Arrays.asList(predicate));

FilterDefinition filter = new FilterDefinition();
filter.setName("AddRequestHeader");
filter.addArg("name", "X-Request-Id");
filter.addArg("value", "123");
route.setFilters(Arrays.asList(filter));
```

### 3.3 RoutePredicateHandlerMapping（路由匹配器）

```java
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {
    
    @Override
    protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
        // 1. 获取所有路由
        return lookupRoute(exchange)
            .map(route -> {
                // 2. 将匹配的路由存储到 exchange 属性中
                exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
                return webHandler;
            });
    }
    
    protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
        // 遍历所有路由，找到第一个匹配的
        return this.routeLocator.getRoutes()
            .filter(route -> {
                // 检查所有 Predicate 是否都匹配
                return route.getPredicate().test(exchange);
            })
            .next(); // 返回第一个匹配的路由
    }
}
```

**匹配过程：**
```java
// 请求：GET /user/1

// 路由 1：user-service
Predicate: Path=/user/**
匹配结果：✅ /user/1 匹配 /user/**

// 路由 2：order-service  
Predicate: Path=/order/**
匹配结果：❌ /user/1 不匹配 /order/**

// 最终选择：user-service
```

### 3.4 Predicate（路由断言）

```java
@FunctionalInterface
public interface Predicate<T> {
    boolean test(T t);  // 测试是否匹配
}
```

**常用断言实现：**

#### PathRoutePredicateFactory（路径断言）
```java
public class PathRoutePredicateFactory {
    
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            PathMatcher pathMatcher = new AntPathMatcher();
            
            // 检查路径是否匹配模式
            for (String pattern : config.getPatterns()) {
                if (pathMatcher.match(pattern, path)) {
                    return true;  // 匹配成功
                }
            }
            return false;  // 匹配失败
        };
    }
}
```

**匹配示例：**
```java
// 配置：Path=/user/**
// 请求路径：/user/1

PathMatcher matcher = new AntPathMatcher();
boolean match = matcher.match("/user/**", "/user/1");  // true

// 匹配规则：
// /user/**  → 匹配 /user/1, /user/123, /user/a/b/c
// /user/*   → 匹配 /user/1, /user/123，不匹配 /user/a/b
// /user/1   → 只匹配 /user/1
```

#### MethodRoutePredicateFactory（方法断言）
```java
public class MethodRoutePredicateFactory {
    
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
            HttpMethod requestMethod = exchange.getRequest().getMethod();
            
            // 检查 HTTP 方法是否匹配
            for (HttpMethod method : config.getMethods()) {
                if (requestMethod == method) {
                    return true;
                }
            }
            return false;
        };
    }
}
```

### 3.5 LoadBalancerClientFilter（负载均衡过滤器）

```java
public class LoadBalancerClientFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        
        // 检查是否是 lb:// 协议
        if (uri != null && "lb".equals(uri.getScheme())) {
            String serviceName = uri.getHost();  // userservice
            
            // 1. 从 Nacos 获取服务实例列表
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            
            // 2. 使用负载均衡算法选择一个实例
            ServiceInstance instance = loadBalancer.choose(serviceName, instances);
            
            // 3. 构建实际的目标地址
            URI requestUrl = URI.create(
                "http://" + instance.getHost() + ":" + instance.getPort() + uri.getPath()
            );
            
            // 4. 更新请求 URL
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
        }
        
        return chain.filter(exchange);
    }
}
```

**负载均衡过程：**
```java
// 原始 URI：lb://userservice/user/1

// 1. 从 Nacos 获取服务实例
List<ServiceInstance> instances = [
    ServiceInstance(host="192.168.1.100", port=8081),
    ServiceInstance(host="192.168.1.101", port=8081),
    ServiceInstance(host="192.168.1.102", port=8081)
];

// 2. 使用轮询算法选择实例（假设当前索引为 0）
ServiceInstance selected = instances.get(0);  // 192.168.1.100:8081

// 3. 构建实际 URL
URI actualUrl = "http://192.168.1.100:8081/user/1";

// 4. 下次请求选择索引 1，实现负载均衡
```

### 3.6 GatewayFilter（路由过滤器）

```java
@FunctionalInterface
public interface GatewayFilter {
    Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);
}
```

**常用过滤器实现：**

#### AddRequestHeaderGatewayFilterFactory
```java
public class AddRequestHeaderGatewayFilterFactory {
    
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 添加请求头
            ServerHttpRequest request = exchange.getRequest().mutate()
                .header(config.getName(), config.getValue())
                .build();
            
            // 使用修改后的请求继续过滤器链
            return chain.filter(exchange.mutate().request(request).build());
        };
    }
}
```

**使用示例：**
```yaml
filters:
  - AddRequestHeader=X-Request-Id, 123
```

```java
// 原始请求头
Headers: {
    "Host": "localhost:10010",
    "User-Agent": "curl/7.68.0"
}

// 经过过滤器后
Headers: {
    "Host": "localhost:10010",
    "User-Agent": "curl/7.68.0",
    "X-Request-Id": "123"  ← 新增
}
```

#### StripPrefixGatewayFilterFactory
```java
public class StripPrefixGatewayFilterFactory {
    
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            
            // 去除路径前缀
            String[] parts = path.split("/");
            int partsToStrip = config.getParts();
            
            StringBuilder newPath = new StringBuilder();
            for (int i = partsToStrip + 1; i < parts.length; i++) {
                newPath.append("/").append(parts[i]);
            }
            
            // 更新请求路径
            ServerHttpRequest newRequest = request.mutate()
                .path(newPath.toString())
                .build();
            
            return chain.filter(exchange.mutate().request(newRequest).build());
        };
    }
}
```

**使用示例：**
```yaml
filters:
  - StripPrefix=1
```

```java
// 原始路径：/api/user/1
// StripPrefix=1：去除第 1 段路径
// 结果路径：/user/1

// 原始路径：/api/v1/user/1
// StripPrefix=2：去除前 2 段路径
// 结果路径：/user/1
```

### 3.7 NettyRoutingFilter（HTTP 请求发送）

```java
public class NettyRoutingFilter implements GlobalFilter {
    
    private final HttpClient httpClient;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        ServerHttpRequest request = exchange.getRequest();
        
        // 1. 构建 HTTP 请求
        HttpMethod method = request.getMethod();
        HttpHeaders headers = request.getHeaders();
        
        // 2. 使用 Netty HttpClient 发送请求
        return httpClient
            .request(method)
            .uri(requestUrl)
            .send((req, nettyOutbound) -> {
                // 设置请求头
                headers.forEach((name, values) -> {
                    req.requestHeaders().set(name, values);
                });
                
                // 发送请求体
                return nettyOutbound.send(request.getBody());
            })
            .response((res, nettyInbound) -> {
                // 3. 接收响应
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.valueOf(res.status().code()));
                
                // 设置响应头
                res.responseHeaders().forEach(entry -> {
                    response.getHeaders().add(entry.getKey(), entry.getValue());
                });
                
                // 4. 写入响应体
                return response.writeWith(nettyInbound.receive().retain());
            });
    }
}
```

## 四、实际转发示例

### 示例 1：基本路由转发

**配置：**
```yaml
routes:
  - id: user-service
    uri: lb://userservice
    predicates:
      - Path=/user/**
```

**请求：**
```
GET http://localhost:10010/user/1
```

**转发过程：**

```
步骤 1：接收请求
ServerWebExchange {
    request: {
        method: GET,
        path: /user/1,
        host: localhost:10010
    }
}

步骤 2：路由匹配
遍历路由列表：
  - user-service: Path=/user/** → ✅ 匹配成功
  - order-service: Path=/order/** → ❌ 跳过

选中路由：user-service

步骤 3：解析 URI
原始 URI: lb://userservice
协议: lb (LoadBalance)
服务名: userservice

步骤 4：从 Nacos 获取服务实例
查询服务名: userservice
返回实例列表:
  - 192.168.1.100:8081 (权重: 1)
  - 192.168.1.101:8081 (权重: 1)

步骤 5：负载均衡选择实例
算法: 轮询 (RoundRobin)
当前索引: 0
选中实例: 192.168.1.100:8081

步骤 6：构建目标 URL
http://192.168.1.100:8081/user/1

步骤 7：发送 HTTP 请求
GET http://192.168.1.100:8081/user/1
Headers: {
    Host: 192.168.1.100:8081,
    User-Agent: ReactorNetty/1.0.0,
    ... (其他请求头)
}

步骤 8：接收响应
Status: 200 OK
Body: {"id":1,"name":"张三","age":20}

步骤 9：返回给客户端
```

### 示例 2：带过滤器的路由转发

**配置：**
```yaml
routes:
  - id: user-service
    uri: lb://userservice
    predicates:
      - Path=/user/**
    filters:
      - AddRequestHeader=X-Request-Id, 123
      - AddRequestHeader=X-Gateway, true
```

**请求：**
```
GET http://localhost:10010/user/1
```

**转发过程：**
```
步骤 1：路由匹配
选中路由：user-service

步骤 2：执行过滤器链
Filter 1: AddRequestHeader=X-Request-Id, 123
  → 添加请求头：X-Request-Id: 123

Filter 2: AddRequestHeader=X-Gateway, true
  → 添加请求头：X-Gateway: true

步骤 3：负载均衡
选中实例：192.168.1.100:8081

步骤 4：发送请求
GET http://192.168.1.100:8081/user/1
Headers: {
    Host: 192.168.1.100:8081,
    X-Request-Id: 123,        ← 过滤器添加
    X-Gateway: true,          ← 过滤器添加
    ... (其他请求头)
}
```

### 示例 3：路径重写转发

**配置：**
```yaml
routes:
  - id: user-service
    uri: lb://userservice
    predicates:
      - Path=/api/user/**
    filters:
      - StripPrefix=1  # 去除 /api 前缀
```

**请求：**
```
GET http://localhost:10010/api/user/1
```

**转发过程：**
```
步骤 1：路由匹配
原始路径：/api/user/1
匹配模式：/api/user/**
匹配结果：✅ 成功

步骤 2：执行 StripPrefix 过滤器
原始路径：/api/user/1
去除段数：1 (去除 /api)
新路径：/user/1

步骤 3：负载均衡
选中实例：192.168.1.100:8081

步骤 4：发送请求
GET http://192.168.1.100:8081/user/1  ← 注意路径变化
```

### 示例 4：多条件匹配转发

**配置：**
```yaml
routes:
  - id: user-service-admin
    uri: lb://userservice
    predicates:
      - Path=/user/**
      - Method=POST,PUT,DELETE
      - Header=X-Role, admin
    filters:
      - AddRequestHeader=X-Admin, true
```

**请求 1（匹配成功）：**
```
POST http://localhost:10010/user/1
Headers:
  X-Role: admin
```

**匹配过程：**
```
Predicate 1: Path=/user/** → ✅ /user/1 匹配
Predicate 2: Method=POST,PUT,DELETE → ✅ POST 匹配
Predicate 3: Header=X-Role, admin → ✅ X-Role: admin 匹配

所有断言都匹配 → 路由成功
```

**请求 2（匹配失败）：**
```
GET http://localhost:10010/user/1
Headers:
  X-Role: admin
```

**匹配过程：**
```
Predicate 1: Path=/user/** → ✅ /user/1 匹配
Predicate 2: Method=POST,PUT,DELETE → ❌ GET 不匹配

有断言不匹配 → 路由失败，返回 404
```

## 五、负载均衡详解

### 5.1 负载均衡算法

#### 轮询（RoundRobin）- 默认算法

```java
public class RoundRobinLoadBalancer {
    private AtomicInteger position = new AtomicInteger(0);
    
    public ServiceInstance choose(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        
        // 获取当前位置并递增
        int pos = position.getAndIncrement() % instances.size();
        return instances.get(pos);
    }
}
```

**示例：**
```
服务实例列表：
  - Instance1: 192.168.1.100:8081
  - Instance2: 192.168.1.101:8081
  - Instance3: 192.168.1.102:8081

请求序列：
  请求1 → position=0 → Instance1 (192.168.1.100:8081)
  请求2 → position=1 → Instance2 (192.168.1.101:8081)
  请求3 → position=2 → Instance3 (192.168.1.102:8081)
  请求4 → position=3 % 3 = 0 → Instance1 (192.168.1.100:8081)
  请求5 → position=4 % 4 = 1 → Instance2 (192.168.1.101:8081)
  ...
```

#### 随机（Random）

```java
public class RandomLoadBalancer {
    private Random random = new Random();
    
    public ServiceInstance choose(List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        
        int index = random.nextInt(instances.size());
        return instances.get(index);
    }
}
```

**示例：**
```
服务实例列表：3 个实例

请求序列：
  请求1 → random(0-2) = 1 → Instance2
  请求2 → random(0-2) = 0 → Instance1
  请求3 → random(0-2) = 2 → Instance3
  请求4 → random(0-2) = 1 → Instance2
  ...
```

#### 权重（Weight）

```java
public class WeightedLoadBalancer {
    
    public ServiceInstance choose(List<ServiceInstance> instances) {
        // 计算总权重
        int totalWeight = instances.stream()
            .mapToInt(ServiceInstance::getWeight)
            .sum();
        
        // 生成随机数
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        
        // 根据权重选择实例
        int currentWeight = 0;
        for (ServiceInstance instance : instances) {
            currentWeight += instance.getWeight();
            if (random < currentWeight) {
                return instance;
            }
        }
        
        return instances.get(0);
    }
}
```

**示例：**
```
服务实例列表：
  - Instance1: 权重 1
  - Instance2: 权重 2
  - Instance3: 权重 3
  总权重：6

权重分布：
  [0-1): Instance1 (概率 1/6 ≈ 16.7%)
  [1-3): Instance2 (概率 2/6 ≈ 33.3%)
  [3-6): Instance3 (概率 3/6 = 50%)

请求序列：
  请求1 → random(0-6) = 2 → Instance2
  请求2 → random(0-6) = 4 → Instance3
  请求3 → random(0-6) = 0 → Instance1
  请求4 → random(0-6) = 5 → Instance3
  ...
```

### 5.2 从 Nacos 获取服务实例

```java
public class NacosDiscoveryClient implements DiscoveryClient {
    
    private NamingService namingService;
    
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        try {
            // 1. 从 Nacos 获取服务实例列表
            List<Instance> instances = namingService.selectInstances(
                serviceId,    // 服务名：userservice
                true          // 只获取健康实例
            );
            
            // 2. 转换为 ServiceInstance
            return instances.stream()
                .map(instance -> new NacosServiceInstance(
                    instance.getInstanceId(),
                    serviceId,
                    instance.getIp(),
                    instance.getPort(),
                    instance.getWeight(),
                    instance.getMetadata()
                ))
                .collect(Collectors.toList());
                
        } catch (NacosException e) {
            throw new RuntimeException("Failed to get instances from Nacos", e);
        }
    }
}
```

**Nacos 查询过程：**
```
1. Gateway 发起查询
   ↓
   serviceName: userservice
   ↓
2. Nacos Client 查询本地缓存
   ↓
   缓存命中？
   ├─ 是 → 返回缓存的实例列表
   └─ 否 → 继续
   ↓
3. 发送 HTTP 请求到 Nacos Server
   ↓
   GET /nacos/v1/ns/instance/list
   ?serviceName=userservice
   &healthyOnly=true
   ↓
4. Nacos Server 返回实例列表
   ↓
   {
     "hosts": [
       {
         "ip": "192.168.1.100",
         "port": 8081,
         "weight": 1.0,
         "healthy": true
       },
       {
         "ip": "192.168.1.101",
         "port": 8081,
         "weight": 1.0,
         "healthy": true
       }
     ]
   }
   ↓
5. 更新本地缓存
   ↓
6. 返回实例列表给 Gateway
```

## 六、过滤器执行顺序

### 6.1 过滤器类型

| 过滤器类型 | 作用范围 | 执行时机 |
|-----------|---------|---------|
| GlobalFilter | 所有路由 | 每个请求都执行 |
| GatewayFilter | 特定路由 | 只在匹配的路由上执行 |

### 6.2 执行顺序

```java
// 过滤器执行顺序由 @Order 或 Ordered 接口决定
// 数字越小，优先级越高

@Order(-1)  // 最先执行
public class AuthorizeFilter implements GlobalFilter {
    // 鉴权过滤器
}

@Order(0)
public class LoggingFilter implements GlobalFilter {
    // 日志过滤器
}

@Order(10000)
public class LoadBalancerClientFilter implements GlobalFilter {
    // 负载均衡过滤器
}

@Order(Integer.MAX_VALUE)  // 最后执行
public class NettyRoutingFilter implements GlobalFilter {
    // HTTP 请求发送过滤器
}
```

### 6.3 完整过滤器链

```
请求进入
    ↓
┌─────────────────────────────────────────────────────────────┐
│ GlobalFilter 链（前置处理）                                   │
│ ├─ @Order(-1)  AuthorizeFilter (鉴权)                       │
│ ├─ @Order(0)   LoggingFilter (日志)                         │
│ ├─ @Order(100) RateLimitFilter (限流)                       │
│ └─ ...                                                       │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ GatewayFilter 链（路由过滤器）                                │
│ ├─ AddRequestHeaderGatewayFilter                            │
│ ├─ StripPrefixGatewayFilter                                 │
│ └─ ...                                                       │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ GlobalFilter 链（后置处理）                                   │
│ ├─ @Order(10000) LoadBalancerClientFilter (负载均衡)        │
│ ├─ @Order(10100) NettyRoutingFilter (发送请求)              │
│ └─ @Order(Integer.MAX_VALUE) NettyWriteResponseFilter       │
└─────────────────────────────────────────────────────────────┘
    ↓
响应返回
```

### 6.4 过滤器链执行示例

```java
// 请求：GET /user/1

// 1. AuthorizeFilter (@Order(-1))
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    System.out.println("1. 执行鉴权检查");
    String auth = exchange.getRequest().getQueryParams().getFirst("authorization");
    if (!"admin".equals(auth)) {
        return exchange.getResponse().setComplete();  // 拦截请求
    }
    return chain.filter(exchange);  // 继续下一个过滤器
}

// 2. LoggingFilter (@Order(0))
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    System.out.println("2. 记录请求日志");
    long startTime = System.currentTimeMillis();
    exchange.getAttributes().put("startTime", startTime);
    
    return chain.filter(exchange).then(Mono.fromRunnable(() -> {
        long endTime = System.currentTimeMillis();
        System.out.println("请求耗时：" + (endTime - startTime) + "ms");
    }));
}

// 3. AddRequestHeaderGatewayFilter (路由过滤器)
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    System.out.println("3. 添加请求头");
    ServerHttpRequest request = exchange.getRequest().mutate()
        .header("X-Request-Id", "123")
        .build();
    return chain.filter(exchange.mutate().request(request).build());
}

// 4. LoadBalancerClientFilter (@Order(10000))
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    System.out.println("4. 执行负载均衡");
    // 从 lb://userservice 解析为 http://192.168.1.100:8081
    URI uri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
    ServiceInstance instance = loadBalancer.choose("userservice");
    URI requestUrl = URI.create("http://" + instance.getHost() + ":" + instance.getPort());
    exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
    return chain.filter(exchange);
}

// 5. NettyRoutingFilter (@Order(10100))
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    System.out.println("5. 发送 HTTP 请求");
    URI requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
    // 使用 Netty HttpClient 发送请求
    return httpClient.request(HttpMethod.GET)
        .uri(requestUrl)
        .response((response, body) -> {
            System.out.println("6. 接收响应");
            return exchange.getResponse().writeWith(body);
        });
}

// 输出：
// 1. 执行鉴权检查
// 2. 记录请求日志
// 3. 添加请求头
// 4. 执行负载均衡
// 5. 发送 HTTP 请求
// 6. 接收响应
// 请求耗时：150ms
```

## 七、请求和响应的完整生命周期

### 7.1 请求阶段

```
客户端发起请求
    ↓
GET http://localhost:10010/user/1?authorization=admin
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Gateway Netty Server 接收请求                                │
│ ├─ 解析 HTTP 请求                                            │
│ ├─ 创建 ServerWebExchange                                    │
│ └─ 启动过滤器链                                              │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 过滤器链处理                                                  │
│ ├─ 鉴权：检查 authorization 参数                             │
│ ├─ 日志：记录请求信息                                        │
│ ├─ 路由匹配：找到 user-service 路由                          │
│ ├─ 添加请求头：X-Request-Id=123                              │
│ ├─ 负载均衡：选择 192.168.1.100:8081                         │
│ └─ 发送请求：使用 Netty HttpClient                           │
└─────────────────────────────────────────────────────────────┘
    ↓
GET http://192.168.1.100:8081/user/1
Headers:
  Host: 192.168.1.100:8081
  X-Request-Id: 123
  User-Agent: ReactorNetty/1.0.0
    ↓
┌─────────────────────────────────────────────────────────────┐
│ UserService 处理请求                                         │
│ ├─ 接收请求                                                  │
│ ├─ 执行业务逻辑                                              │
│ └─ 返回响应                                                  │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 响应阶段

```
UserService 返回响应
    ↓
HTTP/1.1 200 OK
Content-Type: application/json
Body: {"id":1,"name":"张三","age":20}
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Gateway 接收响应                                             │
│ ├─ NettyRoutingFilter 接收响应                               │
│ ├─ 读取响应状态码：200                                       │
│ ├─ 读取响应头：Content-Type=application/json                │
│ └─ 读取响应体：{"id":1,"name":"张三","age":20}               │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ 过滤器链后置处理                                              │
│ ├─ 可以修改响应头                                            │
│ ├─ 可以修改响应体                                            │
│ └─ 记录响应日志                                              │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────┐
│ Gateway 返回响应给客户端                                      │
│ ├─ 写入响应状态码                                            │
│ ├─ 写入响应头                                                │
│ └─ 写入响应体                                                │
└─────────────────────────────────────────────────────────────┘
    ↓
客户端接收响应
```

## 八、核心源码分析

### 8.1 DispatcherHandler（请求分发器）

```java
public class DispatcherHandler implements WebHandler {
    
    private List<HandlerMapping> handlerMappings;  // 处理器映射列表
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        // 1. 遍历所有 HandlerMapping，找到能处理该请求的 Handler
        return Flux.fromIterable(this.handlerMappings)
            .concatMap(mapping -> mapping.getHandler(exchange))
            .next()
            .switchIfEmpty(Mono.error(new NotFoundException()))
            .flatMap(handler -> {
                // 2. 执行 Handler
                return invokeHandler(exchange, handler);
            });
    }
}
```

### 8.2 RoutePredicateHandlerMapping（路由处理器映射）

```java
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {
    
    private RouteLocator routeLocator;  // 路由定位器
    
    @Override
    protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
        // 1. 查找匹配的路由
        return lookupRoute(exchange)
            .flatMap(route -> {
                // 2. 将路由信息存储到 exchange 属性中
                exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
                
                // 3. 返回 FilteringWebHandler
                return Mono.just(webHandler);
            });
    }
    
    protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
        // 获取所有路由并进行匹配
        return this.routeLocator.getRoutes()
            .concatMap(route -> Mono.just(route)
                .filterWhen(r -> {
                    // 测试路由的 Predicate 是否匹配
                    exchange.getAttributes().put(GATEWAY_PREDICATE_ROUTE_ATTR, r.getId());
                    return r.getPredicate().apply(exchange);
                })
                .doOnError(e -> logger.error("Error applying predicate for route: " + route.getId(), e))
                .onErrorResume(e -> Mono.empty())
            )
            .next()  // 返回第一个匹配的路由
            .map(route -> {
                logger.debug("Route matched: " + route.getId());
                return route;
            });
    }
}
```

### 8.3 FilteringWebHandler（过滤器处理器）

```java
public class FilteringWebHandler implements WebHandler {
    
    private List<GlobalFilter> globalFilters;  // 全局过滤器列表
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        List<GatewayFilter> gatewayFilters = route.getFilters();  // 路由过滤器
        
        // 1. 合并全局过滤器和路由过滤器
        List<GatewayFilter> combined = new ArrayList<>();
        combined.addAll(globalFilters);
        combined.addAll(gatewayFilters);
        
        // 2. 按 Order 排序
        AnnotationAwareOrderComparator.sort(combined);
        
        // 3. 创建过滤器链并执行
        return new DefaultGatewayFilterChain(combined).filter(exchange);
    }
}
```

### 8.4 DefaultGatewayFilterChain（过滤器链）

```java
private static class DefaultGatewayFilterChain implements GatewayFilterChain {
    
    private final List<GatewayFilter> filters;
    private final int index;
    
    public DefaultGatewayFilterChain(List<GatewayFilter> filters) {
        this.filters = filters;
        this.index = 0;
    }
    
    private DefaultGatewayFilterChain(List<GatewayFilter> filters, int index) {
        this.filters = filters;
        this.index = index;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
        if (this.index < filters.size()) {
            // 获取当前过滤器
            GatewayFilter filter = filters.get(this.index);
            
            // 创建下一个过滤器链（index + 1）
            DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(filters, this.index + 1);
            
            // 执行当前过滤器
            return filter.filter(exchange, chain);
        } else {
            // 所有过滤器执行完毕
            return Mono.empty();
        }
    }
}
```

**执行流程：**
```java
// 过滤器列表：[Filter1, Filter2, Filter3]

// 第 1 次调用 filter()
index = 0
执行 Filter1.filter(exchange, chain)
  ↓
  Filter1 内部调用 chain.filter(exchange)
  ↓
  // 第 2 次调用 filter()
  index = 1
  执行 Filter2.filter(exchange, chain)
    ↓
    Filter2 内部调用 chain.filter(exchange)
    ↓
    // 第 3 次调用 filter()
    index = 2
    执行 Filter3.filter(exchange, chain)
      ↓
      Filter3 内部调用 chain.filter(exchange)
      ↓
      // 第 4 次调用 filter()
      index = 3 >= filters.size()
      返回 Mono.empty()
      ↓
    Filter3 执行完毕
    ↓
  Filter2 执行完毕
  ↓
Filter1 执行完毕
```

## 九、总结

### 9.1 路由转发核心流程

```
1. 接收请求 → ServerWebExchange
2. 路由匹配 → RoutePredicateHandlerMapping
3. 执行过滤器链 → FilteringWebHandler
4. 负载均衡 → LoadBalancerClientFilter
5. 发送请求 → NettyRoutingFilter
6. 接收响应 → NettyWriteResponseFilter
7. 返回客户端
```

### 9.2 关键组件

| 组件 | 作用 |
|------|------|
| ServerWebExchange | 封装请求和响应 |
| RouteDefinition | 路由定义 |
| Predicate | 路由匹配条件 |
| GatewayFilter | 路由过滤器 |
| GlobalFilter | 全局过滤器 |
| LoadBalancer | 负载均衡器 |
| HttpClient | HTTP 客户端 |

### 9.3 转发过程总结

**一句话总结：**
Gateway 通过 Predicate 匹配路由，使用 LoadBalancer 从 Nacos 选择服务实例，经过 Filter 链处理后，使用 Netty HttpClient 将请求转发到目标服务，最后将响应返回给客户端。
