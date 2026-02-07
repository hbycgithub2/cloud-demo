nginx gataway feign nacos 


用户
  ↓
DNS（域名解析）
  ↓
Nginx（负载均衡、静态资源）
  ↓
Gateway（路由、鉴权、限流、熔断）
  ↓
微服务（用户、订单、支付、商品）
  ├─ Feign（服务调用）
  ├─ Ribbon（负载均衡）
  ├─ Sentinel（限流熔断）
  ├─ Nacos（注册发现、配置管理）
  ├─ Seata（分布式事务）
  ├─ Redis（缓存）
  ├─ RocketMQ（消息队列）
  ├─ Elasticsearch（搜索）
  ├─ MySQL（数据库）
  ↓
Sleuth + Zipkin（链路追踪）
Prometheus + Grafana（监控告警）
