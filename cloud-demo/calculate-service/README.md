# 即时算费服务 (Calculate Service)

> **功能**：提供车险即时算费功能的独立微服务

---

## 📁 项目结构

```
calculate-service/
├── pom.xml                                    # Maven配置
├── src/main/
│   ├── java/cn/itcast/calculate/
│   │   ├── CalculateApplication.java          # 启动类
│   │   ├── controller/
│   │   │   └── CalculateController.java       # Controller层
│   │   ├── service/
│   │   │   └── CalculateService.java          # Service层
│   │   └── pojo/
│   │       ├── CalculateRequest.java          # 请求DTO
│   │       └── CalculateResponse.java         # 响应DTO
│   └── resources/
│       └── application.yml                    # 配置文件
└── README.md                                  # 本文件
```

---

## 🚀 启动步骤

### 方法1：在IDEA中启动（推荐）

1. **在IDEA中打开项目**
   - File → Open → 选择 `D:\code\cloud-demo\cloud-demo`

2. **等待Maven下载依赖**
   - 只依赖Spring Boot Web
   - 下载很快（1-2分钟）

3. **运行主类**
   - 找到：`calculate-service/src/main/java/cn/itcast/calculate/CalculateApplication.java`
   - 右键 → Run 'CalculateApplication.main()'

4. **等待启动**
   - 看到：`Started CalculateApplication in XX seconds`
   - 端口：8083

---

### 方法2：命令行启动

```bash
# 进入项目目录
cd D:\code\cloud-demo\cloud-demo

# 编译整个项目
mvn clean install -Dmaven.test.skip=true

# 进入calculate-service目录
cd calculate-service

# 启动服务
mvn spring-boot:run
```

---

## 🧪 测试接口

### 测试1：GET接口（健康检查）

**浏览器访问**：
```
http://localhost:8083/calculate/test
```

**预期返回**：
```json
{
  "success": true,
  "message": "即时算费服务正常运行",
  "service": "calculate-service",
  "port": 8083,
  "timestamp": 1738732800000
}
```

✅ **如果看到这个返回，说明服务启动成功！**

---

### 测试2：POST接口（即时算费）

#### 方法A：PowerShell

```powershell
$body = @{
    kindCode = "050200"
    amount = 100000
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8083/calculate/realtime -Method Post -ContentType "application/json" -Body $body
```

#### 方法B：CMD + curl

```bash
curl -X POST http://localhost:8083/calculate/realtime -H "Content-Type: application/json" -d "{\"kindCode\":\"050200\",\"amount\":100000}"
```

#### 方法C：Postman

1. 创建新请求：
   - Method: **POST**
   - URL: `http://localhost:8083/calculate/realtime`
   - Headers: `Content-Type: application/json`
   - Body (raw JSON):
     ```json
     {
       "kindCode": "050200",
       "amount": 100000
     }
     ```
2. 点击 **Send**

**预期返回**：
```json
{
  "success": true,
  "kindCode": "050200",
  "amount": 100000,
  "premium": 1034.52,
  "rate": 0.014320,
  "discount": 0.7225,
  "ncdRate": 1.0000,
  "message": "计算成功"
}
```

✅ **保费=1034.52元，计算正确！**

---

## 📊 技术说明

### 计算公式

```
保费 = 保额 × 基础费率 × 折扣系数 × NCD系数
     = 100000 × 0.014320 × 0.7225 × 1.0
     = 1034.52元
```

### 技术栈

- **Spring Boot**: 2.3.9.RELEASE
- **Spring Cloud**: Hoxton.SR10
- **Java**: 1.8
- **Lombok**: 1.18.32（简化代码）

### 依赖说明

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

只有这一个依赖，包含：
- Spring Boot核心
- Spring MVC
- Tomcat嵌入式服务器
- Jackson JSON处理

---

## 🏗️ 架构说明

### 三层架构

```
Controller层 (CalculateController)
    ↓ 调用
Service层 (CalculateService)
    ↓ 调用
Pojo层 (CalculateRequest/Response)
```

### 职责划分

- **Controller层**：接收HTTP请求，参数校验，返回响应
- **Service层**：业务逻辑处理，费率计算，折扣计算
- **Pojo层**：数据传输对象，封装请求和响应

---

## 🎯 扩展方向

### 阶段1：添加数据库支持

```xml
<!-- MySQL驱动 -->
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
</dependency>

<!-- MyBatis -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
</dependency>
```

**功能**：
- 从数据库查询费率表
- 保存算费记录
- 查询历史算费记录

---

### 阶段2：添加Redis缓存

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**功能**：
- 缓存热点费率数据
- 提升查询性能
- 减少数据库压力

---

### 阶段3：添加线程池和异步处理

```java
@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolTaskExecutor calculateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("calculate-");
        executor.initialize();
        return executor;
    }
}
```

**功能**：
- 并行查询多个费率
- 异步保存算费记录
- 提升并发性能

---

### 阶段4：集成Nacos注册中心

```xml
<!-- Nacos Discovery -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

**功能**：
- 服务注册与发现
- 与其他微服务通信
- 负载均衡

---

## 📝 接口文档

### 1. 健康检查接口

**接口地址**：`GET /calculate/test`

**请求参数**：无

**响应示例**：
```json
{
  "success": true,
  "message": "即时算费服务正常运行",
  "service": "calculate-service",
  "port": 8083,
  "timestamp": 1738732800000
}
```

---

### 2. 即时算费接口

**接口地址**：`POST /calculate/realtime`

**请求参数**：
```json
{
  "kindCode": "050200",  // 险种代码（必填）
  "amount": 100000       // 保额，单位：元（必填）
}
```

**响应示例**：
```json
{
  "success": true,
  "kindCode": "050200",
  "amount": 100000,
  "premium": 1034.52,
  "rate": 0.014320,
  "discount": 0.7225,
  "ncdRate": 1.0000,
  "message": "计算成功"
}
```

**字段说明**：
- `success`: 是否成功
- `kindCode`: 险种代码
- `amount`: 保额（元）
- `premium`: 保费（元）
- `rate`: 基础费率
- `discount`: 折扣系数
- `ncdRate`: NCD系数（无赔款优待系数）
- `message`: 消息

---

## ❓ 常见问题

### 问题1：端口被占用

**错误**：`Port 8083 was already in use`

**解决**：
1. 修改 `application.yml` 中的端口号
2. 或者关闭占用8083端口的程序

---

### 问题2：依赖下载失败

**错误**：`Could not resolve dependencies`

**解决**：
1. 检查网络连接
2. 配置Maven镜像（阿里云）
3. 删除本地仓库重新下载

---

### 问题3：Lombok不生效

**错误**：`Cannot resolve symbol 'getData'`

**解决**：
1. IDEA安装Lombok插件
2. Settings → Build → Compiler → Annotation Processors → Enable annotation processing

---

## 📞 技术支持

如果遇到问题，请提供：
1. 启动日志（完整的控制台输出）
2. 错误信息（完整的错误堆栈）
3. 测试结果（浏览器或Postman的返回）

---

## 🎉 总结

这是一个功能齐全的独立微服务模块，包含：

✅ **完整的三层架构**（Controller → Service → Pojo）  
✅ **规范的代码结构**（符合Spring Boot最佳实践）  
✅ **详细的注释文档**（每个类和方法都有注释）  
✅ **易于扩展**（可以添加数据库、Redis、线程池等）  
✅ **独立部署**（可以单独启动、测试、部署）

**现在开始启动服务，测试接口吧！** 🚀
