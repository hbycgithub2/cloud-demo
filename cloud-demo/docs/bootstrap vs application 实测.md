# bootstrap.yml vs application.yml 实测对比

## 测试：把 Nacos 配置中心的配置放到 application.yml 会怎样？

---

## 当前配置（正常工作）

### bootstrap.yml
```yaml
spring:
  application:
    name: userservice
  profiles:
    active: dev
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        enabled: true
        file-extension: yaml
        namespace: 49dec1bd-3857-4c14-935c-ac8884e91101
      discovery:
        cluster-name: SH
```

### application.yml
```yaml
server:
  port: 8081
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root
```

### Nacos 配置中心（userservice-dev.yaml）
```yaml
pattern:
  dateformat: yyyy-MM-dd HH:mm:ss
  name: 本地环境local
```

**结果：✅ 正常工作**
- 能读到 Nacos 配置中心的 `pattern.dateformat`
- 访问 `/user/prop` 能看到配置

---

## 测试 1：删除 bootstrap.yml，全部放到 application.yml

### 新的 application.yml
```yaml
server:
  port: 8081

spring:
  application:
    name: userservice
  profiles:
    active: dev
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/cloud_user?useSSL=false
    username: root
    password: root
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        enabled: true
        file-extension: yaml
        namespace: 49dec1bd-3857-4c14-935c-ac8884e91101
      discovery:
        cluster-name: SH
```

### 启动结果

**情况 1：Spring Cloud 2020 之前的版本**
```
❌ 启动失败或读不到 Nacos 配置

原因：
1. Spring Boot 先加载 application.yml
2. 创建 ApplicationContext
3. 初始化 Bean（包括 PatternProperties）
4. PatternProperties 需要 pattern.dateformat
5. 但这时还没连接 Nacos 配置中心
6. 读不到配置，使用默认值 null
7. 如果代码里用了这个配置，可能报 NullPointerException
```

**情况 2：Spring Cloud 2020+ 版本**
```
⚠️ 可能启动成功，但行为不确定

原因：
- 新版本改进了加载顺序
- 但不保证一定能读到 Nacos 配置
- 官方推荐用 spring.config.import
```

---

## 测试 2：实际验证

### 步骤 1：备份 bootstrap.yml
```bash
mv bootstrap.yml bootstrap.yml.bak
```

### 步骤 2：修改 application.yml
把 bootstrap.yml 的内容复制到 application.yml

### 步骤 3：启动服务
```bash
java -jar user-service.jar
```

### 步骤 4：测试配置
```bash
curl http://localhost:8081/user/prop
```

### 预期结果

**如果用的是老版本 Spring Cloud：**
```json
{
  "dateformat": null,  // ← 读不到 Nacos 配置！
  "envSharedValue": null,
  "name": null
}
```

**如果用的是新版本 Spring Cloud 2020+：**
```json
{
  "dateformat": "yyyy-MM-dd HH:mm:ss",  // ← 可能能读到
  "envSharedValue": "...",
  "name": "本地环境local"
}
```

---

## 为什么会这样？

### 加载顺序对比

#### 用 bootstrap.yml（正确）
```
1. 加载 bootstrap.yml
   ↓
2. 创建 BootstrapContext（引导上下文）
   ↓
3. 连接 Nacos 配置中心
   ↓
4. 下载配置：pattern.dateformat = "yyyy-MM-dd HH:mm:ss"
   ↓
5. 加载 application.yml
   ↓
6. 合并配置（Nacos + application.yml）
   ↓
7. 创建 ApplicationContext（应用上下文）
   ↓
8. 初始化 Bean（PatternProperties 能读到配置）
   ↓
9. 启动成功 ✅
```

#### 只用 application.yml（可能有问题）
```
1. 加载 application.yml
   ↓
2. 创建 ApplicationContext（应用上下文）
   ↓
3. 初始化 Bean（PatternProperties 此时读不到 Nacos 配置）
   ↓
4. 尝试连接 Nacos 配置中心（太晚了！）
   ↓
5. 下载配置（但 Bean 已经初始化完了）
   ↓
6. PatternProperties 的值是 null ❌
```

---

## 关键区别

### bootstrap.yml 的特殊性

```java
// Spring Cloud 启动流程
public class SpringApplication {
    
    public ConfigurableApplicationContext run(String... args) {
        // 1. 先创建 BootstrapContext（如果有 bootstrap.yml）
        ConfigurableApplicationContext bootstrapContext = 
            createBootstrapContext();  // ← 这里加载 bootstrap.yml
        
        // 2. 在 BootstrapContext 中连接配置中心
        loadRemoteConfig(bootstrapContext);  // ← 下载 Nacos 配置
        
        // 3. 再创建 ApplicationContext
        ConfigurableApplicationContext context = 
            createApplicationContext();  // ← 这里加载 application.yml
        
        // 4. 合并配置
        mergeConfig(bootstrapContext, context);
        
        return context;
    }
}
```

### application.yml 的普通性

```java
// 如果没有 bootstrap.yml
public class SpringApplication {
    
    public ConfigurableApplicationContext run(String... args) {
        // 1. 直接创建 ApplicationContext
        ConfigurableApplicationContext context = 
            createApplicationContext();  // ← 加载 application.yml
        
        // 2. 初始化 Bean（此时还没连接配置中心）
        initializeBeans(context);  // ← PatternProperties 读不到配置
        
        // 3. 尝试连接配置中心（太晚了）
        loadRemoteConfig(context);
        
        return context;
    }
}
```

---

## 实际案例

### 案例 1：数据库配置在 Nacos

**bootstrap.yml**
```yaml
spring:
  application:
    name: userservice
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        enabled: true
```

**Nacos 配置中心（userservice.yaml）**
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db
    username: root
    password: 123456
```

**结果：✅ 能正常连接数据库**

---

### 案例 2：把配置放到 application.yml

**application.yml**
```yaml
spring:
  application:
    name: userservice
  cloud:
    nacos:
      server-addr: localhost:8848
      config:
        enabled: true
  datasource:
    url: ???  # 想从 Nacos 读取
```

**结果：❌ 启动失败**
```
Error: Cannot load database configuration
Reason: spring.datasource.url is null
```

**原因：**
1. Spring Boot 加载 application.yml
2. 看到 `spring.datasource`，尝试创建 DataSource
3. 但 `url` 是 null（还没从 Nacos 读取）
4. 创建 DataSource 失败
5. 启动失败

---

## Spring Cloud 2020+ 的新方案

### 不用 bootstrap.yml，用 spring.config.import

**application.yml**
```yaml
spring:
  application:
    name: userservice
  config:
    import:
      - optional:nacos:userservice.yaml  # ← 新方式
  cloud:
    nacos:
      server-addr: localhost:8848
```

**工作原理：**
```
1. 加载 application.yml
2. 看到 spring.config.import
3. 暂停加载，先去 Nacos 下载配置
4. 下载完成后，继续加载 application.yml
5. 合并配置
6. 初始化 Bean
```

**优点：**
- 不需要 bootstrap.yml
- 加载顺序更清晰
- 支持多个配置源

**缺点：**
- 需要 Spring Boot 2.4+
- 需要 Spring Cloud 2020+
- 老项目不支持

---

## 总结

### 如果把 Nacos 配置放到 application.yml 会怎样？

| Spring Cloud 版本 | 结果 | 原因 |
|------------------|------|------|
| **2020 之前** | ❌ 读不到 Nacos 配置 | 加载顺序不对，Bean 初始化时还没连接 Nacos |
| **2020+** | ⚠️ 可能能读到 | 改进了加载顺序，但不保证 |
| **2020+ 用 spring.config.import** | ✅ 能正常读取 | 新的配置导入机制 |

### 最佳实践

**推荐方案 1：传统方式（兼容性最好）**
```
bootstrap.yml  → Nacos 配置中心地址
application.yml → 本地配置
```

**推荐方案 2：现代方式（Spring Cloud 2020+）**
```
application.yml → 用 spring.config.import 导入 Nacos 配置
```

**不推荐：**
```
❌ 把 Nacos 配置中心的配置放到 application.yml（老版本不支持）
```

---

## 快速判断你的项目

### 检查 Spring Cloud 版本
```xml
<!-- pom.xml -->
<spring-cloud.version>Hoxton.SR12</spring-cloud.version>  
<!-- ↑ 2020 之前，必须用 bootstrap.yml -->

<spring-cloud.version>2020.0.3</spring-cloud.version>
<!-- ↑ 2020+，可以用 spring.config.import -->
```

### 你的项目（cloud-demo）
```xml
<!-- 看 pom.xml 的 Spring Cloud 版本 -->
<!-- 如果是 Hoxton 或更早 → 必须用 bootstrap.yml -->
<!-- 如果是 2020+ → 可以用 spring.config.import -->
```

**结论：为了兼容性和稳定性，建议继续用 bootstrap.yml！**
