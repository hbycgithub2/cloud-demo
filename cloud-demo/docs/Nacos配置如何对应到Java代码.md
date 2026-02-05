# Nacos 配置如何对应到 Java 代码

## 你看到的 Nacos 配置

```yaml
pattern:
  envSharedValue: nacos-shared-value
```

## 对应的 Java 代码

```java
@ConfigurationProperties(prefix = "pattern")
public class PatternProperties {
    private String envSharedValue;  // ← 自动对应
}
```

---

## 对应规则

### 规则 1：prefix 匹配

```yaml
# Nacos 配置
pattern:           # ← 这是 prefix
  envSharedValue: xxx
  dateformat: xxx
  name: xxx
```

```java
@ConfigurationProperties(prefix = "pattern")  // ← prefix 要一致
public class PatternProperties {
    // ...
}
```

### 规则 2：属性名匹配（驼峰转换）

```yaml
# Nacos 配置（kebab-case 或 camelCase 都可以）
pattern:
  envSharedValue: xxx    # ← camelCase
  env-shared-value: xxx  # ← kebab-case（也可以）
  dateformat: xxx
  date-format: xxx       # ← 也可以
```

```java
public class PatternProperties {
    private String envSharedValue;  // ← Java 驼峰命名
    private String dateformat;
}
```

**Spring Boot 会自动转换：**
- `env-shared-value` → `envSharedValue`
- `date-format` → `dateformat`

---

## 完整对应示例

### Nacos 配置（userservice-dev.yaml）

```yaml
pattern:
  dateformat: yyyy-MM-dd HH:mm:ss
  envSharedValue: nacos-shared-value
  name: 本地环境local
```

### Java 配置类

```java
@Data
@Component
@ConfigurationProperties(prefix = "pattern")
public class PatternProperties {
    private String dateformat;      // ← pattern.dateformat
    private String envSharedValue;  // ← pattern.envSharedValue
    private String name;            // ← pattern.name
}
```

### 对应关系

```
Nacos 配置                    Java 属性
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
pattern.dateformat       →   dateformat
pattern.envSharedValue   →   envSharedValue
pattern.name             →   name
```

---

## Spring Boot 如何实现对应？

### 1. 启动时读取配置

```java
// Spring Boot 启动流程
public class ConfigurationPropertiesBinder {
    
    public void bind(PatternProperties bean) {
        // 1. 获取 @ConfigurationProperties 注解
        ConfigurationProperties annotation = 
            bean.getClass().getAnnotation(ConfigurationProperties.class);
        
        // 2. 获取 prefix
        String prefix = annotation.prefix();  // "pattern"
        
        // 3. 从配置中心读取所有 pattern.* 的配置
        Map<String, Object> config = getConfig(prefix);
        // {
        //   "dateformat": "yyyy-MM-dd HH:mm:ss",
        //   "envSharedValue": "nacos-shared-value",
        //   "name": "本地环境local"
        // }
        
        // 4. 反射设置属性值
        for (Field field : bean.getClass().getDeclaredFields()) {
            String fieldName = field.getName();  // "dateformat"
            Object value = config.get(fieldName);  // "yyyy-MM-dd HH:mm:ss"
            
            field.setAccessible(true);
            field.set(bean, value);  // bean.dateformat = "yyyy-MM-dd HH:mm:ss"
        }
    }
}
```

### 2. 配置变化时自动更新

```java
// Nacos 监听器
public class NacosConfigListener {
    
    public void onConfigChange(String newConfig) {
        // 1. 解析新配置
        Map<String, Object> config = parseYaml(newConfig);
        // {
        //   "pattern.dateformat": "MM月dd日",
        //   "pattern.envSharedValue": "new-value"
        // }
        
        // 2. 获取 PatternProperties Bean
        PatternProperties bean = applicationContext.getBean(PatternProperties.class);
        
        // 3. 更新属性值
        bean.setDateformat(config.get("pattern.dateformat"));
        bean.setEnvSharedValue(config.get("pattern.envSharedValue"));
        
        // 4. 发布刷新事件
        applicationContext.publishEvent(new RefreshEvent());
    }
}
```

---

## 实际运行流程

### 第一步：启动时加载配置

```
1. UserService 启动
   ↓
2. 读取 bootstrap.yml
   spring.application.name: userservice
   spring.profiles.active: dev
   ↓
3. 连接 Nacos，查询配置：userservice-dev.yaml
   ↓
4. Nacos 返回：
   pattern:
     dateformat: yyyy-MM-dd HH:mm:ss
     envSharedValue: nacos-shared-value
     name: 本地环境local
   ↓
5. 创建 PatternProperties Bean
   ↓
6. 根据 @ConfigurationProperties(prefix = "pattern")
   ↓
7. 设置属性值：
   bean.dateformat = "yyyy-MM-dd HH:mm:ss"
   bean.envSharedValue = "nacos-shared-value"
   bean.name = "本地环境local"
   ↓
8. 注入到 UserController
```

### 第二步：使用配置

```java
@RestController
public class UserController {
    
    @Autowired
    private PatternProperties properties;  // ← 注入
    
    @GetMapping("now")
    public String now() {
        // 使用配置
        String format = properties.getDateformat();  // "yyyy-MM-dd HH:mm:ss"
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }
}
```

### 第三步：动态更新

```
1. 在 Nacos 控制台修改配置：
   pattern:
     dateformat: MM月dd日  # ← 改这里
   ↓
2. Nacos 推送变化通知
   ↓
3. UserService 收到通知
   ↓
4. 拉取最新配置
   ↓
5. 更新 PatternProperties：
   bean.dateformat = "MM月dd日"
   ↓
6. 下次调用 /user/now 时，使用新配置
```

---

## 配置文件命名规则

### Data ID 的组成

```
${spring.application.name}-${spring.profiles.active}.${file-extension}
```

### 示例

**bootstrap.yml：**
```yaml
spring:
  application:
    name: userservice      # ← 服务名
  profiles:
    active: dev            # ← 环境
  cloud:
    nacos:
      config:
        file-extension: yaml  # ← 文件格式
```

**Nacos 配置 ID：**
```
userservice-dev.yaml
    ↑       ↑    ↑
    |       |    └─ file-extension
    |       └────── profiles.active
    └────────────── application.name
```

---

## 多个配置文件的对应

### 场景：共享配置

**Nacos 配置中心：**

```
1. userservice-dev.yaml（服务专属配置）
   pattern:
     dateformat: yyyy-MM-dd HH:mm:ss
     name: dev环境

2. shared-config.yaml（共享配置）
   pattern:
     envSharedValue: 共享值
```

**bootstrap.yml：**
```yaml
spring:
  cloud:
    nacos:
      config:
        # 主配置
        file-extension: yaml
        # 共享配置
        shared-configs:
          - data-id: shared-config.yaml
            refresh: true
```

**Java 代码：**
```java
@ConfigurationProperties(prefix = "pattern")
public class PatternProperties {
    private String dateformat;      // 从 userservice-dev.yaml 读取
    private String name;            // 从 userservice-dev.yaml 读取
    private String envSharedValue;  // 从 shared-config.yaml 读取
}
```

**合并后的配置：**
```yaml
pattern:
  dateformat: yyyy-MM-dd HH:mm:ss      # 来自 userservice-dev.yaml
  name: dev环境                         # 来自 userservice-dev.yaml
  envSharedValue: 共享值                # 来自 shared-config.yaml
```

---

## 常见问题

### Q1: 配置名字不一致会怎样？

**Nacos 配置：**
```yaml
pattern:
  dateFormat: xxx  # ← 注意大写 F
```

**Java 代码：**
```java
public class PatternProperties {
    private String dateformat;  // ← 小写 f
}
```

**结果：** ❌ 对应不上，`dateformat` 的值是 `null`

**解决：** 统一命名，推荐用驼峰命名

---

### Q2: prefix 写错了会怎样？

**Nacos 配置：**
```yaml
pattern:
  dateformat: xxx
```

**Java 代码：**
```java
@ConfigurationProperties(prefix = "patter")  // ← 少了一个 n
public class PatternProperties {
    private String dateformat;
}
```

**结果：** ❌ 所有属性都是 `null`

---

### Q3: 如何验证配置是否对应？

**方法 1：查看日志**
```yaml
logging:
  level:
    org.springframework.boot.context.properties: DEBUG
```

**方法 2：提供接口查看**
```java
@GetMapping("prop")
public PatternProperties properties() {
    return properties;  // 返回所有配置
}
```

访问：`http://localhost:8081/user/prop`

---

## 调试技巧

### 1. 打印配置加载过程

```java
@Component
public class ConfigDebugger implements ApplicationListener<ApplicationReadyEvent> {
    
    @Autowired
    private PatternProperties properties;
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        System.out.println("=== 配置加载完成 ===");
        System.out.println("dateformat: " + properties.getDateformat());
        System.out.println("envSharedValue: " + properties.getEnvSharedValue());
        System.out.println("name: " + properties.getName());
    }
}
```

### 2. 监听配置变化

```java
@Component
public class ConfigChangeListener {
    
    @Autowired
    private PatternProperties properties;
    
    @EventListener
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        System.out.println("=== 配置已更新 ===");
        System.out.println("新的 dateformat: " + properties.getDateformat());
    }
}
```

---

## 总结

### 对应规则

```
Nacos 配置                          Java 代码
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Data ID: userservice-dev.yaml  →  bootstrap.yml 配置
                                   - application.name: userservice
                                   - profiles.active: dev
                                   - file-extension: yaml

prefix: pattern                 →  @ConfigurationProperties(prefix = "pattern")

属性: dateformat                →  private String dateformat;
属性: envSharedValue            →  private String envSharedValue;
属性: name                      →  private String name;
```

### 自动对应机制

1. **启动时**：根据 `prefix` 读取配置，反射设置属性值
2. **运行时**：监听配置变化，自动更新属性值
3. **命名转换**：自动处理 kebab-case 和 camelCase

### 关键点

- ✅ `prefix` 要一致
- ✅ 属性名要对应（支持驼峰转换）
- ✅ Data ID 要符合命名规则
- ✅ 用 `@ConfigurationProperties` 而不是 `@Value`（支持自动刷新）

**这就是 Nacos 配置和 Java 代码的对应关系！**
