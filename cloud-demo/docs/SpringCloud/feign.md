Feign：声明式HTTP客户端
Feign是Spring Cloud提供的声明式HTTP客户端，让微服务之间的调用像调用本地方法一样简单。

简化微服务之间的HTTP调用，不用写RestTemplate代码，只需定义接口。


Feign具体使用
定义接口：用@FeignClient("服务名")定义接口，方法上加@GetMapping等注解，像定义Controller一样
启动类开启：启动类加@EnableFeignClients注解
注入调用：@Autowired注入接口，直接调用方法，Feign自动从Nacos查服务地址、负载均衡、发HTTP请求


@Autowired vs @Resource
@Autowired：Spring注解，按类型注入，找不到报错，配合@Qualifier按名称注入
@Resource：JDK注解，按名称注入，找不到再按类型，可指定name属性

@Qualifier
当一个接口有多个实现类时，@Autowired不知道注入哪个，用@Qualifier指定Bean名称。