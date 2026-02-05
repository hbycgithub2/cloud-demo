# 为什么要用 bootstrap.yml？

## 一句话解释

**因为 Nacos 配置中心的配置要先加载，才能用来配置 application.yml！**

---

## 说人话版本

### 问题场景

你想把数据库配置放到 Nacos 配置中心：

```yaml
# Nacos 配置中心里的配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db
    username: root
    password: 123456
```

但是，你怎么告诉 Spring Boot 去哪里找 Nacos 呢？

---

## 死循环问题

### 如果只用 application.yml

```yaml
# application.yml
spring:
  datasource:
    url: ???  # 这个要从 Nacos 读取
  cloud:
    nacos:
      server-addr: localhost:8848  # 但要先知道 Nacos 在哪
```

**问题：**
```
1. Spring Boot 启动，读取 application.yml
2. 看到 datasource 配置，但值在 Nacos 里
3. 想去 Nacos 读配置
4. 但 nacos.server-addr 也在 application.yml 里
5. 而 application.yml 还没读完！
6. 死循环！💥
```

---

## 解决方案：bootstrap.yml

### 加载顺序

```
1. bootstrap.yml（最先加载）
   ↓
2. 从 Nacos 读取配置
   ↓
3. application.yml（后加载，可以用 Nacos 的配置）
   ↓
4. 启动应用
```

### 实际配置

**bootstrap.yml（先加载）**
```yaml
spring:
  application:
    name: userservice
  cloud:
    nacos:
      server-addr: localhost:8848  # ← 告诉 Spring Boot Nacos 在哪
      config:
        enabled: true
        file-extension: yaml
```

**Nacos 配置中心（userservice.yaml）**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db
    username: root
    password: 123456
```

**application.yml（后加载，可以用 Nacos 的配置）**
```yaml
server:
  port: 8081

# 数据库配置已经从 Nacos 读取了，不用写！
```

---

## 类比：找餐厅吃饭

### 只用 application.yml（错误）
```
你：我要去"张三推荐的餐厅"吃饭
朋友：张三是谁？在哪？
你：张三的联系方式在"张三推荐的餐厅"的菜单上
朋友：？？？你还没找到餐厅，怎么看菜单？
你：💥 死循环
```

### 用 bootstrap.yml（正确）
```
你：我先打电话给张三（bootstrap.yml）
张三：我推荐 XX 餐厅，地址是 YY 路
你：好，我去 XX 餐厅（application.yml 用 Nacos 的配置）
朋友：搞定！
```

---

## 实际案例对比

### 案例 1：不用 Nacos 配置中心（只用服务发现）

**OrderService（只用 application.yml）**
```yaml
# application.yml
server:
  port: 8080
spring:
  application:
    name: orderservice
  datasource:
    url: jdbc:mysql://localhost:3306/db  # ← 写死在这里
  cloud:
    nacos:
      server-addr: localhost:8848
      discovery:
        cluster-name: HZ
```

**不需要 bootstrap.yml！** 因为没用 Nacos 配置中心

---

### 案例 2：用 Nacos 配置中心

**UserService（需要 bootstrap.yml）**

**bootstrap.yml（先加载）**
```yaml
spring:
  application:
    name: userservice
  cloud:
    nacos:
      server-addr: localhost:8848  # ← 先告诉 Nacos 在哪
      config:
        enabled: true              # ← 启用配置中心
```

**Nacos 配置中心（userservice.yaml）**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db
    username: root
    password: 123456
pattern:
  dateformat: yyyy-MM-dd HH:mm:ss
```

**application.yml（后加载）**
```yaml
server:
  port: 8081

# 数据库配置从 Nacos 读取，不用写！
```

---

## 启动流程对比

### 只用 application.yml
```
1. 读取 application.yml
2. 创建 DataSource（用 application.yml 里的配置）
3. 注册到 Nacos
4. 启动完成
```

### 用 bootstrap.yml + Nacos 配置中心
```
1. 读取 bootstrap.yml
2. 连接 Nacos 配置中心
3. 从 Nacos 下载配置（datasource、pattern 等）
4. 读取 application.yml
5. 合并配置（Nacos 配置 + application.yml）
6. 创建 DataSource（用 Nacos 的配置）
7. 注册到 Nacos
8. 启动完成
```

---

## 为什么 OrderService 不用 bootstrap.yml？

看配置就知道了：

**OrderService（application.yml）**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cloud_order  # ← 写死的
  cloud:
    nacos:
      discovery:
        cluster-name: HZ  # ← 只用服务发现，不用配置中心
```

**没用 Nacos 配置中心，所以不需要 bootstrap.yml！**

---

## 什么时候必须用 bootstrap.yml？

### 必须用的场景
1. ✅ 使用 Nacos 配置中心
2. ✅ 使用 Spring Cloud Config
3. ✅ 使用 Consul 配置中心
4. ✅ 需要加密配置（Jasypt）

### 不需要用的场景
1. ❌ 只用 Nacos 服务发现（不用配置中心）
2. ❌ 单体应用
3. ❌ 配置都写在 application.yml 里

---

## 配置优先级

```
bootstrap.yml（最高优先级）
    ↓
Nacos 配置中心
    ↓
application.yml
    ↓
application-{profile}.yml
    ↓
命令行参数（最低优先级）
```

### 示例

**bootstrap.yml**
```yaml
spring:
  application:
    name: userservice  # ← 优先级最高
```

**Nacos 配置中心**
```yaml
spring:
  application:
    name: user-service-from-nacos  # ← 会被 bootstrap.yml 覆盖
```

**最终结果：** `userservice`（bootstrap.yml 赢了）

---

## 现代方案：不用 bootstrap.yml

### Spring Cloud 2020+ 新方案

可以不用 bootstrap.yml，改用 `spring.config.import`：

**application.yml（新方式）**
```yaml
spring:
  application:
    name: userservice
  config:
    import:
      - optional:nacos:userservice.yaml  # ← 直接导入 Nacos 配置
  cloud:
    nacos:
      server-addr: localhost:8848
```

**优点：**
- 不用 bootstrap.yml
- 配置更清晰
- 支持 Spring Boot 2.4+

**缺点：**
- 老项目不支持
- 需要升级 Spring Cloud 版本

---

## 总结

### 为什么要用 bootstrap.yml？

**因为要先知道 Nacos 在哪里，才能从 Nacos 读配置！**

### 加载顺序

```
bootstrap.yml → 连接 Nacos → 下载配置 → application.yml → 启动应用
```

### 什么时候用？

| 场景 | 是否需要 bootstrap.yml |
|------|----------------------|
| 只用 Nacos 服务发现 | ❌ 不需要 |
| 用 Nacos 配置中心 | ✅ 需要 |
| 单体应用 | ❌ 不需要 |
| Spring Cloud 2020+ | ⚠️ 可以用 spring.config.import 代替 |

### 项目里的实际情况

- **OrderService**: 只用 application.yml（不用配置中心）
- **UserService**: 用 bootstrap.yml（用了配置中心）

---

## 快速验证

### 测试 1：删除 bootstrap.yml 会怎样？

```bash
# 删除 user-service/src/main/resources/bootstrap.yml
# 启动 UserService

结果：
❌ 启动失败！
错误：无法连接 Nacos 配置中心
原因：不知道 Nacos 在哪里
```

### 测试 2：把 bootstrap.yml 的内容放到 application.yml

```yaml
# application.yml
spring:
  application:
    name: userservice
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        enabled: true
```

```bash
# 启动 UserService

结果：
⚠️ 可能启动成功，但读不到 Nacos 配置中心的配置
原因：加载顺序不对，application.yml 加载时还没连接 Nacos
```

---

## 最佳实践

### 推荐方案 1：传统方式
```
bootstrap.yml  → Nacos 配置中心地址
application.yml → 本地配置
```

### 推荐方案 2：现代方式（Spring Cloud 2020+）
```
application.yml → 用 spring.config.import 导入 Nacos 配置
```

### 不推荐
```
❌ 把所有配置都放 application.yml（读不到 Nacos 配置）
❌ 把所有配置都放 bootstrap.yml（不符合规范）
```

**记住：bootstrap.yml 是"引导配置"，只放最基础的配置（Nacos 地址、服务名），其他配置放 application.yml 或 Nacos 配置中心！**
