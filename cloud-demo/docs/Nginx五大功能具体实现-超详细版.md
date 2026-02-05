# Nginx 五大功能具体实现 - 超详细版

## 目录

1. [反向代理](#功能-1反向代理)
2. [负载均衡](#功能-2负载均衡)
3. [静态资源服务器](#功能-3静态资源服务器)
4. [动静分离](#功能-4动静分离)
5. [HTTPS 加密](#功能-5https-加密)

---

## 功能 1：反向代理

### 什么是反向代理？

**人话：Nginx 就像一个"中介"，客户端不直接访问后端服务器，而是通过 Nginx 转发**

```
正向代理（翻墙）：
  你 → 代理服务器 → 外网
  你知道代理服务器的存在

反向代理（Nginx）：
  客户端 → Nginx → 后端服务器
  客户端不知道后端服务器的存在
```

---

### 配置文件

```nginx
# nginx.conf

server {
    listen 80;                    # 监听 80 端口
    server_name www.example.com;  # 域名

    # 反向代理配置
    location /api/ {
        # 转发给后端服务器
        proxy_pass http://localhost:8080;
        
        # 设置请求头（把客户端信息传递给后端）
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

---

### 详细流程图

```
┌─────────────────────────────────────────────────────────────┐
│                    反向代理完整流程                            │
└─────────────────────────────────────────────────────────────┘

步骤 1：客户端发送请求
  ↓
  客户端（浏览器）
  发送请求：http://www.example.com/api/user/1
  
  客户端只知道 Nginx 的地址（www.example.com）
  不知道后端服务器的地址

步骤 2：DNS 解析
  ↓
  DNS 服务器
  www.example.com → 192.168.1.100（Nginx 的 IP）

步骤 3：请求到达 Nginx
  ↓
  Nginx（192.168.1.100:80）
  收到请求：GET /api/user/1
  
  请求信息：
    - 请求方法：GET
    - 请求路径：/api/user/1
    - 客户端 IP：203.0.113.50
    - User-Agent：Mozilla/5.0...

步骤 4：Nginx 匹配 location
  ↓
  Nginx 配置：
    location /api/ {
        proxy_pass http://localhost:8080;
    }
  
  匹配结果：✅ 匹配成功（/api/user/1 以 /api/ 开头）

步骤 5：Nginx 转发请求
  ↓
  Nginx 转发给后端服务器
  目标地址：http://localhost:8080/api/user/1
  
  Nginx 添加请求头：
    Host: www.example.com
    X-Real-IP: 203.0.113.50（客户端真实 IP）
    X-Forwarded-For: 203.0.113.50
    X-Forwarded-Proto: http

步骤 6：后端服务器处理请求
  ↓
  后端服务器（localhost:8080）
  收到请求：GET /api/user/1
  
  后端可以通过请求头获取客户端信息：
    - X-Real-IP: 203.0.113.50（客户端真实 IP）
    - Host: www.example.com（客户端访问的域名）
  
  处理请求：
    1. 查询数据库
    2. 获取用户信息
    3. 返回 JSON 数据

步骤 7：后端返回响应
  ↓
  后端服务器 → Nginx
  响应数据：
    {
      "id": 1,
      "name": "张三",
      "age": 25
    }

步骤 8：Nginx 返回响应给客户端
  ↓
  Nginx → 客户端
  响应数据：
    {
      "id": 1,
      "name": "张三",
      "age": 25
    }

步骤 9：客户端收到响应
  ↓
  客户端（浏览器）
  显示数据：张三，25 岁

────────────────────────────────────────────────────────────

关键点：
  1. 客户端只知道 Nginx 的地址（www.example.com）
  2. 客户端不知道后端服务器的地址（localhost:8080）
  3. Nginx 把客户端信息（IP、域名）通过请求头传递给后端
  4. 后端服务器被"隐藏"了，客户端无法直接访问
```

---

### 为什么要隐藏后端服务器？

```
安全性：
  ✅ 客户端无法直接访问后端服务器
  ✅ 防止 DDoS 攻击（攻击者不知道后端服务器的 IP）
  ✅ 可以在 Nginx 层做安全防护（防火墙、限流）

灵活性：
  ✅ 可以随时更换后端服务器（客户端不受影响）
  ✅ 可以添加多台后端服务器（负载均衡）
  ✅ 可以在 Nginx 层做统一处理（日志、监控）
```

---

### 实际例子：淘宝

```
你访问淘宝：
  ↓
  浏览器输入：https://www.taobao.com
  ↓
  DNS 解析：www.taobao.com → Nginx 的 IP
  ↓
  请求到达 Nginx
  ↓
  Nginx 转发给后端服务器（可能有 10000 台）
  ↓
  后端服务器处理请求
  ↓
  Nginx 返回响应
  ↓
  你看到淘宝首页

你只知道 www.taobao.com，不知道后端有多少台服务器！
```

---

## 功能 2：负载均衡

### 什么是负载均衡？

**人话：把请求分配给多台服务器，避免单台服务器压力过大**

```
问题：
  1 台服务器每秒只能处理 1000 个请求
  如果有 10000 个请求，怎么办？

解决：
  用 10 台服务器，每台处理 1000 个请求
  Nginx 把请求分配给不同的服务器

就像：
  超市有 10 个收银台，顾客排队时，会分配到不同的收银台
```

---

### 配置文件

```nginx
# nginx.conf

# 定义后端服务器组
upstream backend_servers {
    # 服务器 1
    server localhost:8081;
    
    # 服务器 2
    server localhost:8082;
    
    # 服务器 3
    server localhost:8083;
}

server {
    listen 80;
    server_name www.example.com;

    location /api/ {
        # 转发给后端服务器组（负载均衡）
        proxy_pass http://backend_servers;
        
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

### 详细流程图（轮询策略）

```
┌─────────────────────────────────────────────────────────────┐
│              负载均衡完整流程（轮询策略）                       │
└─────────────────────────────────────────────────────────────┘

Nginx 配置：
  upstream backend_servers {
      server localhost:8081;  # 服务器 1
      server localhost:8082;  # 服务器 2
      server localhost:8083;  # 服务器 3
  }

────────────────────────────────────────────────────────────

请求 1：
  客户端 A 发送请求
    ↓
    http://www.example.com/api/user/1
    ↓
  Nginx 收到请求
    ↓
  Nginx 选择服务器（轮询）
    当前轮到：服务器 1
    ↓
  Nginx 转发给服务器 1
    http://localhost:8081/api/user/1
    ↓
  服务器 1 处理请求
    ↓
  返回响应

────────────────────────────────────────────────────────────

请求 2：
  客户端 B 发送请求
    ↓
    http://www.example.com/api/user/2
    ↓
  Nginx 收到请求
    ↓
  Nginx 选择服务器（轮询）
    当前轮到：服务器 2
    ↓
  Nginx 转发给服务器 2
    http://localhost:8082/api/user/2
    ↓
  服务器 2 处理请求
    ↓
  返回响应

────────────────────────────────────────────────────────────

请求 3：
  客户端 C 发送请求
    ↓
    http://www.example.com/api/user/3
    ↓
  Nginx 收到请求
    ↓
  Nginx 选择服务器（轮询）
    当前轮到：服务器 3
    ↓
  Nginx 转发给服务器 3
    http://localhost:8083/api/user/3
    ↓
  服务器 3 处理请求
    ↓
  返回响应

────────────────────────────────────────────────────────────

请求 4：
  客户端 D 发送请求
    ↓
    http://www.example.com/api/user/4
    ↓
  Nginx 收到请求
    ↓
  Nginx 选择服务器（轮询）
    当前轮到：服务器 1（重新开始轮询）
    ↓
  Nginx 转发给服务器 1
    http://localhost:8081/api/user/4
    ↓
  服务器 1 处理请求
    ↓
  返回响应

────────────────────────────────────────────────────────────

轮询规则：
  请求 1 → 服务器 1
  请求 2 → 服务器 2
  请求 3 → 服务器 3
  请求 4 → 服务器 1（重新开始）
  请求 5 → 服务器 2
  请求 6 → 服务器 3
  ...

效果：
  ✅ 3 台服务器平均分配请求
  ✅ 每台服务器处理 1/3 的请求
  ✅ 性能提升 3 倍
```

---

### 4 种负载均衡策略

#### 策略 1：轮询（默认）

```nginx
upstream backend_servers {
    server localhost:8081;
    server localhost:8082;
    server localhost:8083;
}
```

**效果：**
```
请求 1 → 服务器 1
请求 2 → 服务器 2
请求 3 → 服务器 3
请求 4 → 服务器 1
...

依次轮流分配，平均分配
```

---

#### 策略 2：权重（weight）

```nginx
upstream backend_servers {
    server localhost:8081 weight=3;  # 权重 3
    server localhost:8082 weight=2;  # 权重 2
    server localhost:8083 weight=1;  # 权重 1
}
```

**效果：**
```
10 个请求：
  - 5 个请求 → 服务器 1（权重 3，占比 50%）
  - 3 个请求 → 服务器 2（权重 2，占比 33%）
  - 2 个请求 → 服务器 3（权重 1，占比 17%）

适用场景：
  服务器性能不同
  性能好的服务器权重高，处理更多请求
```

**详细流程：**
```
请求 1 → 服务器 1（权重 3）
请求 2 → 服务器 1（权重 3）
请求 3 → 服务器 1（权重 3）
请求 4 → 服务器 2（权重 2）
请求 5 → 服务器 2（权重 2）
请求 6 → 服务器 3（权重 1）
请求 7 → 服务器 1（重新开始）
...
```

---

#### 策略 3：IP 哈希（ip_hash）

```nginx
upstream backend_servers {
    ip_hash;  # 启用 IP 哈希
    server localhost:8081;
    server localhost:8082;
    server localhost:8083;
}
```

**效果：**
```
客户端 IP: 192.168.1.100
  ↓
  hash(192.168.1.100) % 3 = 1
  ↓
  始终转发给服务器 1

客户端 IP: 192.168.1.101
  ↓
  hash(192.168.1.101) % 3 = 2
  ↓
  始终转发给服务器 2

客户端 IP: 192.168.1.102
  ↓
  hash(192.168.1.102) % 3 = 0
  ↓
  始终转发给服务器 3

适用场景：
  需要会话保持（Session）
  同一个客户端始终访问同一个服务器
```

**详细流程：**
```
客户端 A（IP: 192.168.1.100）：
  请求 1 → 服务器 1
  请求 2 → 服务器 1
  请求 3 → 服务器 1
  ...
  所有请求都转发给服务器 1

客户端 B（IP: 192.168.1.101）：
  请求 1 → 服务器 2
  请求 2 → 服务器 2
  请求 3 → 服务器 2
  ...
  所有请求都转发给服务器 2
```

---

#### 策略 4：最少连接（least_conn）

```nginx
upstream backend_servers {
    least_conn;  # 启用最少连接
    server localhost:8081;
    server localhost:8082;
    server localhost:8083;
}
```

**效果：**
```
当前状态：
  服务器 1：10 个连接
  服务器 2：5 个连接
  服务器 3：8 个连接

新请求来了：
  ↓
  Nginx 选择连接数最少的服务器
  ↓
  转发给服务器 2（连接数最少）

适用场景：
  请求处理时间不同
  避免某个服务器负载过高
```

**详细流程：**
```
初始状态：
  服务器 1：0 个连接
  服务器 2：0 个连接
  服务器 3：0 个连接

请求 1（处理时间 10 秒）：
  ↓
  转发给服务器 1
  服务器 1：1 个连接

请求 2（处理时间 10 秒）：
  ↓
  转发给服务器 2
  服务器 2：1 个连接

请求 3（处理时间 10 秒）：
  ↓
  转发给服务器 3
  服务器 3：1 个连接

请求 4（处理时间 1 秒）：
  ↓
  服务器 1：1 个连接
  服务器 2：1 个连接
  服务器 3：1 个连接
  ↓
  转发给服务器 1（假设）
  服务器 1：2 个连接

1 秒后，请求 4 处理完成：
  服务器 1：1 个连接（请求 4 完成）
  服务器 2：1 个连接
  服务器 3：1 个连接

请求 5：
  ↓
  转发给服务器 1（连接数最少）
```

---

## 功能 3：静态资源服务器

### 什么是静态资源？

**人话：不需要后端处理的文件，直接返回就行**

```
静态资源：
  - HTML 文件（index.html）
  - CSS 文件（style.css）
  - JavaScript 文件（app.js）
  - 图片（logo.png）
  - 视频（video.mp4）
  - 字体文件（font.ttf）

特点：
  不需要查询数据库
  不需要计算
  直接返回文件内容
```

---

### 配置文件

```nginx
# nginx.conf

server {
    listen 80;
    server_name www.example.com;

    # 静态资源配置
    location /static/ {
        # 静态资源目录
        root /usr/share/nginx/html;
        
        # 缓存配置（加速访问）
        expires 30d;  # 缓存 30 天
        add_header Cache-Control "public, immutable";
        
        # 开启 gzip 压缩（减少传输大小）
        gzip on;
        gzip_types text/css application/javascript image/png;
    }
}
```

---

### 详细流程图

```
┌─────────────────────────────────────────────────────────────┐
│              静态资源服务器完整流程                            │
└─────────────────────────────────────────────────────────────┘

准备工作：
  在服务器上创建静态资源目录
  /usr/share/nginx/html/static/
    ├── logo.png
    ├── style.css
    └── app.js

────────────────────────────────────────────────────────────

步骤 1：客户端发送请求
  ↓
  客户端（浏览器）
  发送请求：http://www.example.com/static/logo.png

步骤 2：请求到达 Nginx
  ↓
  Nginx 收到请求
  请求路径：/static/logo.png

步骤 3：Nginx 匹配 location
  ↓
  Nginx 配置：
    location /static/ {
        root /usr/share/nginx/html;
    }
  
  匹配结果：✅ 匹配成功（/static/logo.png 以 /static/ 开头）

步骤 4：Nginx 计算文件路径
  ↓
  root = /usr/share/nginx/html
  请求路径 = /static/logo.png
  
  文件路径 = root + 请求路径
           = /usr/share/nginx/html + /static/logo.png
           = /usr/share/nginx/html/static/logo.png

步骤 5：Nginx 读取文件
  ↓
  Nginx 从磁盘读取文件
  文件路径：/usr/share/nginx/html/static/logo.png
  
  检查文件是否存在：
    if (文件存在) {
        ✅ 读取文件内容
    } else {
        ❌ 返回 404 Not Found
    }

步骤 6：Nginx 设置响应头
  ↓
  Content-Type: image/png
  Content-Length: 12345（文件大小）
  Cache-Control: public, max-age=2592000（缓存 30 天）
  Expires: Mon, 23 Feb 2026 12:00:00 GMT

步骤 7：Nginx 返回响应
  ↓
  Nginx → 客户端
  响应数据：logo.png 的二进制内容

步骤 8：客户端收到响应
  ↓
  客户端（浏览器）
  显示图片：logo.png
  
  浏览器缓存图片（30 天内不再请求）

────────────────────────────────────────────────────────────

关键点：
  1. Nginx 直接从磁盘读取文件
  2. 不需要经过后端服务器
  3. 速度快（Nginx 用 C 语言写的，性能高）
  4. 可以缓存（减少重复请求）
```

---

### 为什么用 Nginx 处理静态资源？

```
方案 1：后端服务器处理静态资源 ❌
  客户端 → 后端服务器 → 读取文件 → 返回
  
  问题：
    ❌ 浪费后端服务器资源（后端应该处理业务逻辑）
    ❌ 性能低（Java 读取文件比 Nginx 慢）
    ❌ 占用后端服务器带宽

方案 2：Nginx 处理静态资源 ✅
  客户端 → Nginx → 读取文件 → 返回
  
  优点：
    ✅ 快（Nginx 用 C 语言写的，性能高）
    ✅ 不占用后端服务器资源
    ✅ 可以缓存（减少重复请求）
    ✅ 可以压缩（减少传输大小）
```

---

### 实际例子：淘宝商品图片

```
淘宝有 10 亿张商品图片
如果用后端服务器处理：
  ❌ 后端服务器压力巨大
  ❌ 性能低
  ❌ 成本高

用 Nginx 处理：
  ✅ Nginx 直接返回图片
  ✅ 速度快
  ✅ 成本低

实际流程：
  你访问淘宝商品页面
    ↓
  浏览器请求商品图片
    http://img.taobao.com/static/product/123.jpg
    ↓
  Nginx 收到请求
    ↓
  Nginx 直接从磁盘读取图片
    ↓
  Nginx 返回图片
    ↓
  你看到商品图片

不需要经过后端服务器！
```

---

## 功能 4：动静分离

### 什么是动静分离？

**人话：静态资源 Nginx 直接返回，动态资源 Nginx 转发给后端**

```
静态资源：
  不需要后端处理的文件
  例如：HTML、CSS、JS、图片

动态资源：
  需要后端处理的请求
  例如：查询数据库、计算、业务逻辑

动静分离：
  静态资源 → Nginx 直接返回（快）
  动态资源 → Nginx 转发给后端（慢，但必须）
```

---

### 配置文件

```nginx
# nginx.conf

upstream backend_servers {
    server localhost:8080;
}

server {
    listen 80;
    server_name www.example.com;

    # 静态资源配置
    location /static/ {
        root /usr/share/nginx/html;
        expires 30d;
    }

    # 动态资源配置（转发给后端）
    location /api/ {
        proxy_pass http://backend_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 首页配置
    location / {
        root /usr/share/nginx/html;
        index index.html;
    }
}
```

---

### 详细流程图

```
┌─────────────────────────────────────────────────────────────┐
│                  动静分离完整流程                              │
└─────────────────────────────────────────────────────────────┘

场景：用户访问淘宝商品页面

────────────────────────────────────────────────────────────

请求 1：访问商品页面（HTML）
  ↓
  客户端发送请求
    http://www.taobao.com/product/123.html
    ↓
  Nginx 收到请求
    ↓
  Nginx 匹配 location
    location / {
        root /usr/share/nginx/html;
    }
    ↓
  Nginx 直接返回 HTML 文件
    /usr/share/nginx/html/product/123.html
    ↓
  客户端收到 HTML
    ✅ 快（Nginx 直接返回）

────────────────────────────────────────────────────────────

请求 2：加载 CSS 文件
  ↓
  客户端发送请求
    http://www.taobao.com/static/style.css
    ↓
  Nginx 收到请求
    ↓
  Nginx 匹配 location
    location /static/ {
        root /usr/share/nginx/html;
    }
    ↓
  Nginx 直接返回 CSS 文件
    /usr/share/nginx/html/static/style.css
    ↓
  客户端收到 CSS
    ✅ 快（Nginx 直接返回）

────────────────────────────────────────────────────────────

请求 3：加载 JavaScript 文件
  ↓
  客户端发送请求
    http://www.taobao.com/static/app.js
    ↓
  Nginx 收到请求
    ↓
  Nginx 匹配 location
    location /static/ {
        root /usr/share/nginx/html;
    }
    ↓
  Nginx 直接返回 JS 文件
    /usr/share/nginx/html/static/app.js
    ↓
  客户端收到 JS
    ✅ 快（Nginx 直接返回）

────────────────────────────────────────────────────────────

请求 4：加载商品图片
  ↓
  客户端发送请求
    http://www.taobao.com/static/product/123.jpg
    ↓
  Nginx 收到请求
    ↓
  Nginx 匹配 location
    location /static/ {
        root /usr/share/nginx/html;
    }
    ↓
  Nginx 直接返回图片
    /usr/share/nginx/html/static/product/123.jpg
    ↓
  客户端收到图片
    ✅ 快（Nginx 直接返回）

────────────────────────────────────────────────────────────

请求 5：查询商品详情（动态数据）
  ↓
  客户端发送请求
    http://www.taobao.com/api/product/123
    ↓
  Nginx 收到请求
    ↓
  Nginx 匹配 location
    location /api/ {
        proxy_pass http://backend_servers;
    }
    ↓
  Nginx 转发给后端服务器
    http://localhost:8080/api/product/123
    ↓
  后端服务器处理请求
    1. 查询数据库
    2. 获取商品详情
    3. 返回 JSON 数据
    ↓
  Nginx 返回响应给客户端
    {
      "id": 123,
      "name": "iPhone 15",
      "price": 5999
    }
    ↓
  客户端收到数据
    ⚠️ 慢（需要查询数据库）

────────────────────────────────────────────────────────────

请求 6：查询用户购物车（动态数据）
  ↓
  客户端发送请求
    http://www.taobao.com/api/cart
    ↓
  Nginx 收到请求
    ↓
  Nginx 匹配 location
    location /api/ {
        proxy_pass http://backend_servers;
    }
    ↓
  Nginx 转发给后端服务器
    http://localhost:8080/api/cart
    ↓
  后端服务器处理请求
    1. 查询数据库
    2. 获取购物车数据
    3. 返回 JSON 数据
    ↓
  Nginx 返回响应给客户端
    {
      "items": [
        {"id": 1, "name": "iPhone 15", "quantity": 1}
      ]
    }
    ↓
  客户端收到数据
    ⚠️ 慢（需要查询数据库）

────────────────────────────────────────────────────────────

总结：
  静态资源（HTML、CSS、JS、图片）：
    ✅ Nginx 直接返回
    ✅ 快（不需要查询数据库）
    ✅ 不占用后端资源

  动态资源（API 接口）：
    ⚠️ Nginx 转发给后端
    ⚠️ 慢（需要查询数据库）
    ⚠️ 占用后端资源

  效果：
    ✅ 静态资源快，不影响后端
    ✅ 动态资源慢，但必须查询数据库
    ✅ 互不影响，性能最优
```

---

### 为什么要动静分离？

```
不分离（所有请求都经过后端）：
  客户端 → 后端服务器 → 返回
  
  问题：
    ❌ 静态资源也要经过后端（浪费资源）
    ❌ 后端压力大
    ❌ 性能低

动静分离：
  静态资源：客户端 → Nginx → 返回
  动态资源：客户端 → Nginx → 后端 → 返回
  
  优点：
    ✅ 静态资源快（Nginx 直接返回）
    ✅ 后端只处理动态资源（压力小）
    ✅ 性能高
```

---

### 实际例子：淘宝商品页面

```
淘宝商品页面包含：
  - HTML 文件（1 个）
  - CSS 文件（5 个）
  - JavaScript 文件（10 个）
  - 图片（20 个）
  - API 接口（5 个）

不分离：
  所有请求都经过后端
  后端需要处理 41 个请求
  ❌ 后端压力大

动静分离：
  静态资源（36 个）：Nginx 直接返回
  动态资源（5 个）：后端处理
  ✅ 后端只需要处理 5 个请求
  ✅ 性能提升 8 倍
```

---

## 功能 5：HTTPS 加密

### 什么是 HTTPS？

**人话：HTTPS 就是加密的 HTTP，防止别人偷看你的数据**

```
HTTP（不安全）：
  明文传输
  例如：密码是 123456，直接传输
  别人可以偷看

HTTPS（安全）：
  加密传输
  例如：密码是 123456，加密后传输（变成乱码）
  别人看不懂
```

---

### 为什么需要 HTTPS？

```
场景 1：登录
  你输入密码：123456
  
  HTTP：
    密码明文传输：123456
    ❌ 黑客可以偷看密码
  
  HTTPS：
    密码加密传输：a8f5d2c9e1b4...（乱码）
    ✅ 黑客看不懂

场景 2：支付
  你输入银行卡号：6222 1234 5678 9012
  
  HTTP：
    银行卡号明文传输
    ❌ 黑客可以偷看银行卡号
  
  HTTPS：
    银行卡号加密传输
    ✅ 黑客看不懂

场景 3：个人信息
  你的姓名、地址、电话
  
  HTTP：
    明文传输
    ❌ 黑客可以偷看
  
  HTTPS：
    加密传输
    ✅ 黑客看不懂
```

---

### Nginx HTTPS 配置

```nginx
# nginx.conf

server {
    listen 443 ssl;                     # 监听 443 端口（HTTPS）
    server_name www.example.com;

    # SSL 证书配置
    ssl_certificate /etc/nginx/ssl/cert.pem;        # 证书文件
    ssl_certificate_key /etc/nginx/ssl/key.pem;     # 私钥文件

    # SSL 协议配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # 静态资源
    location /static/ {
        root /usr/share/nginx/html;
    }

    # 动态资源（转发给后端）
    location /api/ {
        proxy_pass http://localhost:8080;  # 注意：这里是 HTTP，不是 HTTPS
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto https;  # 告诉后端这是 HTTPS 请求
    }
}

# HTTP 自动跳转到 HTTPS
server {
    listen 80;
    server_name www.example.com;
    
    # 重定向到 HTTPS
    return 301 https://$server_name$request_uri;
}
```

---

### 详细流程图

```
┌─────────────────────────────────────────────────────────────┐
│                  HTTPS 加密完整流程                            │
└─────────────────────────────────────────────────────────────┘

步骤 1：客户端发送请求
  ↓
  客户端（浏览器）
  发送请求：https://www.example.com/api/user/1
  
  注意：这是 HTTPS，不是 HTTP

步骤 2：DNS 解析
  ↓
  DNS 服务器
  www.example.com → 192.168.1.100（Nginx 的 IP）

步骤 3：建立 TCP 连接
  ↓
  客户端 → Nginx
  建立 TCP 连接（端口 443）

步骤 4：SSL/TLS 握手（加密协商）
  ↓
  客户端 → Nginx：你好，我想建立 HTTPS 连接
  Nginx → 客户端：好的，这是我的证书（cert.pem）
  
  客户端验证证书：
    1. 检查证书是否过期
    2. 检查证书是否被信任的 CA 签发
    3. 检查证书的域名是否匹配
  
  验证结果：✅ 证书有效
  
  客户端 → Nginx：证书验证通过，我们用这个密钥加密
  Nginx → 客户端：好的，我们开始加密通信

步骤 5：客户端发送加密请求
  ↓
  客户端加密请求数据：
    原始数据：GET /api/user/1
    加密后：a8f5d2c9e1b4...（乱码）
  
  客户端 → Nginx：a8f5d2c9e1b4...（加密数据）

步骤 6：Nginx 解密请求
  ↓
  Nginx 收到加密数据：a8f5d2c9e1b4...
  Nginx 解密：GET /api/user/1
  
  Nginx 知道客户端想访问：/api/user/1

步骤 7：Nginx 转发给后端（HTTP 明文）
  ↓
  Nginx → 后端服务器
  http://localhost:8080/api/user/1
  
  注意：
    ✅ 客户端到 Nginx：HTTPS 加密（安全）
    ✅ Nginx 到后端：HTTP 明文（内网，安全）
  
  为什么 Nginx 到后端用 HTTP？
    1. 内网环境，相对安全
    2. 后端不需要处理 HTTPS（简化后端）
    3. 性能更好（不需要加密解密）

步骤 8：后端处理请求
  ↓
  后端服务器处理请求
  查询数据库，返回数据：
    {
      "id": 1,
      "name": "张三",
      "age": 25
    }

步骤 9：后端返回响应（HTTP 明文）
  ↓
  后端 → Nginx
  HTTP 响应：
    {
      "id": 1,
      "name": "张三",
      "age": 25
    }

步骤 10：Nginx 加密响应
  ↓
  Nginx 加密响应数据：
    原始数据：{"id": 1, "name": "张三", "age": 25}
    加密后：x9k2m5n8p1q4...（乱码）

步骤 11：Nginx 返回加密响应
  ↓
  Nginx → 客户端
  加密数据：x9k2m5n8p1q4...

步骤 12：客户端解密响应
  ↓
  客户端收到加密数据：x9k2m5n8p1q4...
  客户端解密：{"id": 1, "name": "张三", "age": 25}
  
  客户端显示数据：张三，25 岁

────────────────────────────────────────────────────────────

关键点：
  1. 客户端到 Nginx：HTTPS 加密（安全）
  2. Nginx 到后端：HTTP 明文（内网，安全）
  3. 后端不需要处理 HTTPS（简化后端）
  4. 所有加密解密工作由 Nginx 完成
```

---

### HTTPS 加密原理（简化版）

```
┌─────────────────────────────────────────────────────────────┐
│                  HTTPS 加密原理                                │
└─────────────────────────────────────────────────────────────┘

对称加密：
  加密和解密用同一个密钥
  
  例如：
    密钥：abc123
    原始数据：hello
    加密：hello + abc123 = x9k2m5（乱码）
    解密：x9k2m5 + abc123 = hello
  
  问题：
    怎么把密钥（abc123）安全地传递给对方？
    如果密钥被偷看，就不安全了

非对称加密：
  加密和解密用不同的密钥
  
  公钥：公开的，任何人都可以用来加密
  私钥：保密的，只有自己可以用来解密
  
  例如：
    公钥：public_key
    私钥：private_key
    
    加密：hello + public_key = x9k2m5（乱码）
    解密：x9k2m5 + private_key = hello
  
  优点：
    公钥可以公开，不怕被偷看
    只有私钥可以解密，私钥保密

HTTPS 加密流程：
  1. 客户端 → Nginx：你好，我想建立 HTTPS 连接
  2. Nginx → 客户端：这是我的公钥（public_key）
  3. 客户端生成对称密钥（abc123）
  4. 客户端用公钥加密对称密钥：abc123 + public_key = x9k2m5
  5. 客户端 → Nginx：x9k2m5（加密的对称密钥）
  6. Nginx 用私钥解密：x9k2m5 + private_key = abc123
  7. 现在客户端和 Nginx 都有对称密钥（abc123）
  8. 后续通信用对称密钥加密（快）

为什么不全用非对称加密？
  非对称加密慢，对称加密快
  所以：
    1. 用非对称加密传递对称密钥（安全）
    2. 用对称密钥加密数据（快）
```

---

### HTTP 自动跳转到 HTTPS

```nginx
# HTTP 服务器（端口 80）
server {
    listen 80;
    server_name www.example.com;
    
    # 重定向到 HTTPS
    return 301 https://$server_name$request_uri;
}
```

**流程：**
```
客户端访问：http://www.example.com/api/user/1
  ↓
Nginx 收到请求（端口 80）
  ↓
Nginx 返回 301 重定向
  Location: https://www.example.com/api/user/1
  ↓
客户端自动跳转到 HTTPS
  https://www.example.com/api/user/1
  ↓
Nginx 收到请求（端口 443）
  ↓
建立 HTTPS 连接
```

---

### 如何获取 SSL 证书？

```
方案 1：免费证书（Let's Encrypt）✅
  - 免费
  - 自动续期
  - 适合个人网站、小型项目
  
  获取方式：
    1. 安装 Certbot
    2. 运行命令：certbot --nginx -d www.example.com
    3. 自动配置 Nginx

方案 2：付费证书
  - 收费（几百到几千元/年）
  - 更高的信任度
  - 适合企业网站、电商网站
  
  获取方式：
    1. 购买证书（阿里云、腾讯云）
    2. 下载证书文件
    3. 配置 Nginx

方案 3：自签名证书（仅用于测试）
  - 免费
  - 浏览器会警告"不安全"
  - 仅用于开发测试
  
  生成方式：
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
      -keyout key.pem -out cert.pem
```

---

## 总结：5 大功能对比

| 功能 | 作用 | 配置关键字 | 适用场景 |
|------|------|-----------|---------|
| **反向代理** | 隐藏后端服务器 | `proxy_pass` | 所有场景 |
| **负载均衡** | 分配请求给多台服务器 | `upstream` | 高并发场景 |
| **静态资源服务器** | 直接返回文件 | `root` | 有静态资源的场景 |
| **动静分离** | 静态资源 Nginx 返回，动态资源转发后端 | `location` | 有静态资源 + 动态接口的场景 |
| **HTTPS 加密** | 加密传输 | `ssl_certificate` | 需要安全的场景（登录、支付）|

---

## 完整架构图

```
┌─────────────────────────────────────────────────────────────┐
│              Nginx 完整架构（5 大功能）                        │
└─────────────────────────────────────────────────────────────┘

客户端（浏览器、手机 APP）
  ↓
  https://www.example.com

┌─────────────────────────────────────────────────────────────┐
│ Nginx（端口 443）                                             │
│                                                               │
│ 功能 1：反向代理                                              │
│   - 隐藏后端服务器                                            │
│                                                               │
│ 功能 2：负载均衡                                              │
│   - 分配请求给多台服务器                                      │
│                                                               │
│ 功能 3：静态资源服务器                                        │
│   - 直接返回 HTML、CSS、JS、图片                              │
│                                                               │
│ 功能 4：动静分离                                              │
│   - 静态资源：Nginx 直接返回                                  │
│   - 动态资源：转发给后端                                      │
│                                                               │
│ 功能 5：HTTPS 加密                                            │
│   - 客户端到 Nginx：HTTPS 加密                                │
│   - Nginx 到后端：HTTP 明文                                   │
└─────────────────────────────────────────────────────────────┘
  ↓
  静态资源请求（/static/）→ Nginx 直接返回
  动态资源请求（/api/）→ 转发给后端

┌─────────────────────────────────────────────────────────────┐
│ 后端服务器集群（HTTP）                                        │
│   - 服务器 1（localhost:8081）                                │
│   - 服务器 2（localhost:8082）                                │
│   - 服务器 3（localhost:8083）                                │
└─────────────────────────────────────────────────────────────┘
  ↓
  处理业务逻辑
  查询数据库
  返回响应
```

---

## 一句话总结

**Nginx 的 5 大功能：**

1. **反向代理**：客户端 → Nginx → 后端（隐藏后端）
2. **负载均衡**：Nginx 把请求分配给多台服务器（提升性能）
3. **静态资源服务器**：Nginx 直接返回文件（快）
4. **动静分离**：静态资源 Nginx 返回，动态资源转发后端（优化）
5. **HTTPS 加密**：客户端到 Nginx 加密，Nginx 到后端明文（安全）

**推荐架构：客户端 → Nginx（HTTPS + 动静分离 + 负载均衡）→ 后端服务器集群**

