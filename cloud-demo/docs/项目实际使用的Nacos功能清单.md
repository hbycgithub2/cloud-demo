# 项目实际使用的 Nacos 功能清单

## ✅ 已实现的功能

### 1. ✅ 服务发现（核心功能）
**证据：**
```yaml
# order-service/application.yml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      discovery:
        cluster-name: HZ
```

```java
// OrderService.java - 通过服务名调用
@FeignClient("userservice")  // ← 用服务名，不用 IP
public interface UserClient {
    @GetMapping("/user/{id}")
    User findById(@PathVariable("id") Long id);
}
```

**实际效果：** OrderService 通过 `userservice` 服务名调用 UserService，不用写死 IP

---

### 2. ✅ 配置管理（已配置）
**证据：**
```xml
<!-- user-service/pom.xml -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

```yaml
# user-service/bootstrap.yml
spring:
  cloud:
    nacos:
      config:
        enabled: true              # ← 启用配置中心
        file-extension: yaml
        namespace: 49dec1bd-xxx    # ← dev 环境
```

```java
// PatternProperties.java - 读取 Nacos 配置
@ConfigurationProperties(prefix = "pattern")
public class PatternProperties {
    private String dateformat;      // 从 Nacos 读取
    private String envSharedValue;
    private String name;
}
```

**实际效果：** UserService 从 Nacos 读取配置，可以动态修改不重启

---

### 3. ✅ 集群管理（已配置）
**证据：**
```yaml
# order-service (杭州集群)
discovery:
  cluster-name: HZ  # ← 杭州

# user-service (上海集群)
discovery:
  cluster-name: SH  # ← 上海
```

```yaml
# order-service 配置同城优先
userservice:
  ribbon:
    NFLoadBalancerRuleClassName: com.alibaba.cloud.nacos.ribbon.NacosRule
```

**实际效果：** 杭州的 OrderService 优先调用杭州的 UserService（如果有的话）

---

### 4. ✅ 健康检查（自动开启）
**证据：**
```yaml
# order-service/application.yml
discovery:
  ephemeral: true  # ← 临时实例，自动心跳检查
```

**实际效果：** 
- 服务每 5 秒发心跳
- 15 秒没心跳 → 标记不健康
- 30 秒没心跳 → 自动剔除

---

### 5. ⚠️ 权重配置（功能可用，但需手动配置）
**状态：** 代码里没配置，但可以在 Nacos 控制台手动设置

**如何使用：**
1. 打开 Nacos 控制台：http://localhost:8848/nacos
2. 进入"服务管理" → "服务列表" → "userservice"
3. 点击"详情" → 修改实例权重（0-10）

**实际效果：** 可以做灰度发布，但项目里没用到

---

### 6. ✅ 命名空间（已配置）
**证据：**
```yaml
# user-service/bootstrap.yml
nacos:
  config:
    namespace: 49dec1bd-3857-4c14-935c-ac8884e91101  # ← dev 环境

# order-service/application.yml (注释掉了)
#namespace: 1a72929d-8643-4c75-a511-e086974e8429  # dev环境
```

**实际效果：** UserService 使用了命名空间隔离 dev 环境，OrderService 没用（注释掉了）

---

## 总结表格

| 功能 | 状态 | OrderService | UserService | 说明 |
|------|------|--------------|-------------|------|
| **服务发现** | ✅ 已实现 | ✅ | ✅ | 核心功能，两个服务都用了 |
| **配置管理** | ✅ 已实现 | ❌ | ✅ | 只有 UserService 用了 |
| **集群管理** | ✅ 已实现 | ✅ HZ | ✅ SH | 配置了不同集群 |
| **健康检查** | ✅ 自动开启 | ✅ | ✅ | 临时实例自动心跳 |
| **权重配置** | ⚠️ 可用未用 | - | - | 可以在控制台手动配置 |
| **命名空间** | ⚠️ 部分使用 | ❌ | ✅ | UserService 用了，OrderService 注释掉了 |

---

## 实际运行的功能

### 核心功能（100% 在用）
1. **服务发现** - OrderService 调用 UserService
2. **负载均衡** - 多个 UserService 实例自动轮询
3. **健康检查** - 服务挂了自动剔除

### 高级功能（部分在用）
4. **配置管理** - 只有 UserService 在用
5. **集群管理** - 配置了但可能没多实例测试
6. **命名空间** - UserService 用了，OrderService 没用

### 可用但未用
7. **权重配置** - 可以在控制台配，代码里没用

---

## 快速验证

### 验证服务发现
```bash
# 1. 启动 UserService (8081)
# 2. 启动 OrderService (8080)
# 3. 访问订单接口
curl http://localhost:8080/order/101

# 能返回订单+用户信息 = 服务发现成功
```

### 验证配置管理
```bash
# 1. 访问配置接口
curl http://localhost:8081/user/prop

# 2. 在 Nacos 控制台修改 userservice-dev.yaml
# 3. 再次访问，配置已更新 = 配置管理成功
```

### 验证健康检查
```bash
# 1. 启动 2 个 UserService 实例
# 2. 关掉一个
# 3. OrderService 还能正常调用 = 健康检查成功
```

---

## 结论

**这个项目实现了 Nacos 的 4 个核心功能：**
1. ✅ 服务发现（最重要）
2. ✅ 配置管理（UserService）
3. ✅ 集群管理（配置了 HZ/SH）
4. ✅ 健康检查（自动开启）

**2 个功能部分实现：**
5. ⚠️ 命名空间（只有 UserService 用了）
6. ⚠️ 权重配置（可用但没配）

**总体来说：核心功能都有，是个完整的 Nacos 学习项目！**
