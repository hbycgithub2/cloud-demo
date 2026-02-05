# Nacos 服务注册 - 说人话版

## 就三步，超简单

### 第一步：加个依赖
在 `pom.xml` 里加这个：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

**这个依赖干啥的？** 就是让你的服务能自动注册到 Nacos，不用你写代码

### 第二步：写配置文件

就写这几行，告诉 Spring Boot 三件事：

```yaml
server:
  port: 8081                          # 我的端口是多少

spring:
  application:
    name: userservice                 # 我叫什么名字
  cloud:
    nacos:
      server-addr: localhost:8848     # Nacos 在哪里
```

**就这么简单！** 不用写 IP，Spring Boot 会自动获取你电脑的 IP

### 第三步：启动项目

**就这样！** 你什么都不用做，Spring Boot 启动时会自动：
1. 读配置文件，知道了服务名叫 `userservice`，端口是 `8081`
2. 自动获取你电脑的 IP（比如 `192.168.26.1`）
3. 把这些信息打包发给 Nacos：`localhost:8848`
4. 每隔 5 秒发个心跳，告诉 Nacos："我还活着"

---

## 你问的问题

### Q: 服务名 userservice 怎么注入的？
**A**: 你在配置文件写的 `spring.application.name: userservice`，Spring Boot 启动时自动读取

### Q: IP 和端口怎么注入的？
**A**: 
- **端口**：你写的 `server.port: 8081`
- **IP**：Spring Boot 自动获取你电脑的网卡 IP（比如 192.168.26.1），不用你管

**重点：** `localhost:8848` 是 Nacos 服务器的地址，不是你服务的 IP！

看图秒懂：
```
你的配置文件：
├─ server.port: 8081                    ← 你服务的端口
├─ spring.application.name: userservice ← 你服务的名字
└─ nacos.server-addr: localhost:8848    ← Nacos 在哪里（不是你的 IP！）

自动获取的 IP：
└─ 192.168.26.1  ← Spring Boot 自动获取你电脑的网卡 IP

最终注册到 Nacos 的信息：
{
  "服务名": "userservice",
  "IP": "192.168.26.1",      ← 这个是自动获取的！
  "端口": 8081
}
```

### Q: 怎么注册到 Nacos 的？
**A**: Spring Boot 启动后，自动发 HTTP 请求给 Nacos：

```
你的服务（192.168.26.1:8081）
        ↓
    发送请求
        ↓
Nacos 服务器（localhost:8848）

请求内容：
POST http://localhost:8848/nacos/v1/ns/instance
{
  "serviceName": "userservice",
  "ip": "192.168.26.1",    ← 这个是自动获取你电脑的 IP
  "port": 8081
}
```

**划重点：**
- `localhost:8848` = Nacos 服务器的地址（你要把信息发给谁）
- `192.168.26.1` = 你服务的 IP（自动获取的，告诉 Nacos 别人怎么找到你）

---

## 看图秒懂

```
你的项目启动
    ↓
Spring Boot 读配置文件
    ↓
知道了：服务名=userservice, 端口=8081, Nacos地址=localhost:8848
    ↓
自动获取本机 IP = 192.168.26.1
    ↓
自动发请求给 Nacos："嘿，我是 userservice，我在 192.168.26.1:8081"
    ↓
Nacos 记录下来："好的，收到了"
    ↓
每 5 秒发心跳："我还活着"
```

---

## 验证是否成功

打开浏览器访问：`http://localhost:8848/nacos`

在"服务管理"里能看到 `userservice` 和 `orderservice`，就成功了！

---

## 总结一句话

**加个依赖 + 写三行配置 = 自动注册到 Nacos，啥代码都不用写！**
