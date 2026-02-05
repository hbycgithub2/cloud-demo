# Nacos 配置管理说明

## 一、配置文件关系

```
bootstrap.yml (最先加载)
    ↓
连接 Nacos，下载 userservice-dev.yaml
    ↓
application.yml (最后加载，优先级最高)
```

**配置优先级：** `application.yml` > Nacos 配置 > `bootstrap.yml`

---

## 二、配置文件详解

### 1. bootstrap.yml - 引导配置

**文件位置：** `user-service/src/main/resources/bootstrap.yml`

**作用：** 告诉 Spring Boot 去哪里（Nacos）下载什么配置

**配置内容：**
```yaml
spring:
  application:
    name: userservice          # 服务名
  profiles:
    active: dev                # 环境
  cloud:
    nacos:
      server-addr: localhost:8848  # Nacos 地址
      config:
        enabled: true          # 启用 Nacos 配置中心
        file-extension: yaml   # 配置文件格式
        namespace: 49dec1bd-3857-4c14-935c-ac8884e91101  # dev 命名空间 ID
      discovery:
        cluster-name: SH       # 集群名称：上海
```

**关键配置说明：**
- `name`: 服务名，用于构建 Nacos Data ID
- `profiles.active`: 环境标识（dev/test/prod）
- `namespace`: 命名空间 ID，用于环境隔离
- `file-extension`: 配置文件后缀

**加载时机：** 最早，在 ApplicationContext 创建之前

---

### 2. Nacos 远程配置 - userservice-dev.yaml

**配置位置：** Nacos 控制台 → 配置管理 → 配置列表 → dev 命名空间

**Data ID 命名规则：**
```
${spring.application.name}-${spring.profiles.active}.${file-extension}
= userservice-dev.yaml
```

**配置内容：**
```yaml
pattern:
  envSharedValue: nacos-shared-value
```

**作用：**
- 集中管理配置
- 支持动态刷新（配合 `@RefreshScope` 注解）
- 多环境隔离（通过命名空间）

**访问地址：** http://192.168.26.1:8848/nacos

---

### 3. application.yml - 本地配置

**文件位置：** `user-service/src/main/resources/application.yml`

**配置内容：**
```yaml
server:
  port: 8081

spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root

mybatis:
  type-aliases-package: cn.itcast.user.pojo
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    cn.itcast: info
    cn.itcast.user.mapper: off
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{traceId},%X{spanId}] [%thread] %-5level %logger{36} - %msg%n"
    dateformat: MM-dd HH:mm:ss:SSS

pattern:
  name: 本地环境local
  dateformat: yyyy-MM-dd HH:mm:ss
```

**作用：**
- 本地默认配置
- 优先级最高，会覆盖 Nacos 同名配置
- 适合配置数据库连接、端口等本地环境相关配置

---

## 三、配置读取流程

### 启动阶段

```
1. 加载 bootstrap.yml
   ↓
2. 连接 Nacos (localhost:8848)
   ↓
3. 根据 namespace + 服务名 + 环境，下载配置
   Data ID: userservice-dev.yaml
   Namespace: 49dec1bd-3857-4c14-935c-ac8884e91101
   ↓
4. 加载 application.yml（覆盖同名配置）
   ↓
5. 合并配置，注入到 PatternProperties
   @ConfigurationProperties(prefix = "pattern")
   ↓
6. Spring 容器启动完成
```

### 配置注入

**Java 配置类：**
```java
@Data
@Component
@ConfigurationProperties(prefix = "pattern")
public class PatternProperties {
    private String dateformat;      // 从 application.yml 读取
    private String envSharedValue;  // 从 Nacos 读取
    private String name;            // 从 application.yml 读取
}
```

**Controller 使用：**
```java
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private PatternProperties properties;
    
    @GetMapping("prop")
    public PatternProperties properties(){
        return properties;  // 返回合并后的配置
    }
}
```

---

## 四、访问接口流程

### 请求：`GET http://localhost:8081/user/prop`

```
浏览器发起请求
   ↓
Tomcat 接收请求 (8081端口)
   ↓
DispatcherServlet 路由到 UserController
   ↓
@GetMapping("prop")
public PatternProperties properties() {
    return properties;  // 返回已注入的配置对象
}
   ↓
Spring MVC 将对象转为 JSON
   ↓
返回响应
```

### 响应结果

```json
{
  "dateformat": "yyyy-MM-dd HH:mm:ss",
  "envSharedValue": "nacos-shared-value",
  "name": "本地环境local"
}
```

### 配置来源

| 字段 | 来源 | 值 |
|------|------|-----|
| `name` | application.yml | `本地环境local` |
| `dateformat` | application.yml | `yyyy-MM-dd HH:mm:ss` |
| `envSharedValue` | Nacos (userservice-dev.yaml) | `nacos-shared-value` |

---

## 五、常见问题

### 1. 为什么 envSharedValue 为 null？

**原因：**
- 没有指定 `namespace`，默认去 `public` 命名空间查找
- 配置文件在 `dev` 命名空间，找不到配置

**解决：**
在 `bootstrap.yml` 中添加：
```yaml
namespace: 49dec1bd-3857-4c14-935c-ac8884e91101
```

### 2. 本地配置覆盖了 Nacos 配置怎么办？

**原因：** `application.yml` 优先级高于 Nacos 配置

**解决：**
- 删除 `application.yml` 中的同名配置
- 或者将本地配置移到 Nacos 中统一管理

### 3. 如何实现配置动态刷新？

**方法：**
在需要刷新的 Bean 上添加 `@RefreshScope` 注解：
```java
@RefreshScope
@RestController
@RequestMapping("/user")
public class UserController {
    // ...
}
```

修改 Nacos 配置后，配置会自动刷新，无需重启服务。

---

## 六、Nacos 配置管理最佳实践

### 1. 配置分类

- **bootstrap.yml**: 服务名、Nacos 地址、命名空间等引导配置
- **Nacos 配置**: 业务配置、开关、动态参数
- **application.yml**: 数据库连接、端口等本地环境配置

### 2. 命名空间使用

- `public`: 公共配置
- `dev`: 开发环境
- `test`: 测试环境
- `prod`: 生产环境

### 3. 配置文件命名

- 服务级配置: `${服务名}-${环境}.yaml`
- 共享配置: `shared-${环境}.yaml`

### 4. 配置优先级

```
application.yml (最高)
  ↓
Nacos 配置
  ↓
bootstrap.yml (最低)
```

---

## 七、相关文件

- `bootstrap.yml`: `user-service/src/main/resources/bootstrap.yml`
- `application.yml`: `user-service/src/main/resources/application.yml`
- `PatternProperties.java`: `user-service/src/main/java/cn/itcast/user/config/PatternProperties.java`
- `UserController.java`: `user-service/src/main/java/cn/itcast/user/web/UserController.java`

---

## 八、Nacos 控制台

- **地址**: http://192.168.26.1:8848/nacos
- **用户名**: nacos
- **密码**: nacos
- **命名空间**: dev (ID: 49dec1bd-3857-4c14-935c-ac8884e91101)
