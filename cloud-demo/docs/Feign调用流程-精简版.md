# Feign 调用流程（精简版）

## 启动时（准备工具）

Spring 看到 UserClient 接口
看到接口上有 @FeignClient("userservice") 和 @GetMapping("/user/{id}")
生成代理对象时，把这些信息都记到代理对象里了
代理对象内部记着：
- 我是给 UserClient 接口用的
- 服务名是 userservice
- findById 方法对应 GET /user/{id}

## 注入时（@Autowired）

Spring 看到要注入 UserClient 类型
从容器里找 UserClient 类型的对象
找到那个代理对象（它已经记住了所有信息）
注入给 userClient 变量

## 调用时

你调 userClient.findById(1)
代理对象拦截，看自己记的信息：
- 哦，findById 方法对应 GET /user/{id}
- 服务名是 userservice
组装：GET /user/1 到 userservice

拦截器组装信息：
- 服务名：userservice
- HTTP 方法：GET
- 路径：/user/{id}，把 {id} 替换成 1 → /user/1

知道了：要发 GET /user/1 到 userservice

从本地缓存查 userservice 的地址
[192.168.26.1:8081, 192.168.26.2:8081]

Ribbon 负载均衡选一个（轮询）
这次选：192.168.26.1:8081

拼完整 URL + 发 HTTP 请求
GET http://192.168.26.1:8081/user/1

UserService 收到请求 → 查数据库 → 返回 JSON
{"id":1,"username":"柳岩","address":"湖南长沙"}

Jackson 把 JSON 转成 User 对象

返回：User(id=1, username="柳岩")
