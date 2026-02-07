# RocketMQ Windows 安装指南（5分钟）

## 一、下载RocketMQ

### 方式1：官网下载（推荐）
```
1. 访问：https://rocketmq.apache.org/download
2. 选择版本：rocketmq-all-5.1.4-bin-release.zip（最新稳定版）
3. 下载到：D:\software\（或任意目录）
```

### 方式2：百度网盘（备用）
```
如果官网下载慢，可以搜索"RocketMQ 5.1.4 百度网盘"
```

## 二、解压安装

### 1. 解压文件
```
解压到：D:\rocketmq\
目录结构：
D:\rocketmq\
├── bin\          # 启动脚本
├── conf\         # 配置文件
├── lib\          # 依赖库
└── LICENSE
```

### 2. 配置环境变量（可选）
```
1. 右键"此电脑" → 属性 → 高级系统设置 → 环境变量
2. 新建系统变量：
   变量名：ROCKETMQ_HOME
   变量值：D:\rocketmq
3. 编辑Path，添加：%ROCKETMQ_HOME%\bin
```

## 三、修改配置（重要）

### 1. 修改NameServer内存（避免内存不足）
```
编辑文件：D:\rocketmq\bin\runserver.cmd

找到这一行（约第18行）：
set "JAVA_OPT=%JAVA_OPT% -server -Xms2g -Xmx2g -Xmn1g"

修改为（降低内存）：
set "JAVA_OPT=%JAVA_OPT% -server -Xms512m -Xmx512m -Xmn256m"
```

### 2. 修改Broker内存（避免内存不足）
```
编辑文件：D:\rocketmq\bin\runbroker.cmd

找到这一行（约第17行）：
set "JAVA_OPT=%JAVA_OPT% -server -Xms8g -Xmx8g"

修改为（降低内存）：
set "JAVA_OPT=%JAVA_OPT% -server -Xms1g -Xmx1g"
```

## 四、启动RocketMQ

### 1. 启动NameServer
```bash
# 新开CMD窗口（管理员权限）
cd D:\rocketmq\bin
start mqnamesrv.cmd

# 看到以下信息表示启动成功：
# The Name Server boot success. serializeType=JSON
```

### 2. 启动Broker
```bash
# 新开CMD窗口（管理员权限）
cd D:\rocketmq\bin
start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true

# 看到以下信息表示启动成功：
# The broker[...] boot success. serializeType=JSON
```

### 3. 验证启动成功
```bash
# 查看Java进程
jps -l

# 应该看到：
# xxxxx org.apache.rocketmq.namesrv.NamesrvStartup
# xxxxx org.apache.rocketmq.broker.BrokerStartup
```

## 五、常见问题

### 问题1：找不到JAVA_HOME
```
错误：Please set the JAVA_HOME variable in your environment

解决：
1. 确认已安装JDK 1.8+
2. 配置JAVA_HOME环境变量：
   变量名：JAVA_HOME
   变量值：C:\Program Files\Java\jdk1.8.0_xxx
3. 验证：java -version
```

### 问题2：内存不足
```
错误：Java HotSpot(TM) 64-Bit Server VM warning: INFO: os::commit_memory

解决：
按照"三、修改配置"降低内存配置
```

### 问题3：端口被占用
```
错误：Address already in use: bind

解决：
1. 查看占用端口的进程：netstat -ano | findstr 9876
2. 杀死进程：taskkill /F /PID <进程ID>
3. 重新启动RocketMQ
```

### 问题4：启动后自动关闭
```
原因：内存配置过高或JDK版本不兼容

解决：
1. 降低内存配置（见"三、修改配置"）
2. 确认JDK版本：java -version（推荐1.8）
3. 查看日志：D:\rocketmq\logs\
```

## 六、快速测试

### 1. 发送测试消息
```bash
cd D:\rocketmq\bin
tools.cmd org.apache.rocketmq.example.quickstart.Producer

# 看到：SendResult [sendStatus=SEND_OK, ...] 表示发送成功
```

### 2. 接收测试消息
```bash
cd D:\rocketmq\bin
tools.cmd org.apache.rocketmq.example.quickstart.Consumer

# 看到：ConsumeMessageThread... Receive New Messages: [...] 表示接收成功
```

## 七、关闭RocketMQ

### 方式1：直接关闭CMD窗口
```
关闭NameServer和Broker的CMD窗口即可
```

### 方式2：使用脚本关闭
```bash
# 关闭Broker
cd D:\rocketmq\bin
mqshutdown.cmd broker

# 关闭NameServer
mqshutdown.cmd namesrv
```

## 八、RocketMQ控制台（可选）

### 1. 下载控制台
```
GitHub：https://github.com/apache/rocketmq-dashboard
下载：rocketmq-dashboard-1.0.0.jar
```

### 2. 启动控制台
```bash
java -jar rocketmq-dashboard-1.0.0.jar --server.port=8080 --rocketmq.config.namesrvAddr=127.0.0.1:9876
```

### 3. 访问控制台
```
浏览器打开：http://localhost:8080
可以查看Topic、消息、消费者组等信息
```

## 九、目录说明

```
D:\rocketmq\
├── bin\                    # 启动脚本
│   ├── mqnamesrv.cmd      # 启动NameServer
│   ├── mqbroker.cmd       # 启动Broker
│   ├── mqshutdown.cmd     # 关闭脚本
│   ├── tools.cmd          # 工具脚本
│   ├── runserver.cmd      # NameServer启动配置
│   └── runbroker.cmd      # Broker启动配置
├── conf\                   # 配置文件
│   ├── broker.conf        # Broker配置
│   └── logback_*.xml      # 日志配置
├── lib\                    # 依赖库
└── logs\                   # 日志目录（启动后自动创建）
    ├── rocketmqlogs\      # RocketMQ日志
    └── ...
```

## 十、下一步

安装完成后，回到《快速测试指南.md》继续测试RocketMQ Demo项目。

---

## 快速命令汇总

```bash
# 1. 启动NameServer
cd D:\rocketmq\bin
start mqnamesrv.cmd

# 2. 启动Broker
cd D:\rocketmq\bin
start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true

# 3. 验证启动
jps -l

# 4. 关闭（可选）
mqshutdown.cmd broker
mqshutdown.cmd namesrv
```

---

**安装时间：5分钟**  
**难度：⭐（简单）**  
**推荐版本：RocketMQ 5.1.4**
