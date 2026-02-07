
Seata：分布式事务解决方案
阿里开源的分布式事务框架，解决微服务架构下的数据一致性问题。



Seata解决微服务分布式事务问题，通过@GlobalTransactional注解，保证多个微服务的操作要么都成功，要么都回滚，实现数据一致性。


单体应用（本地事务）
在方法上加 @Transactional一个事务，增删除改操作，要么都成功，要么都失败 ✅


微服务（分布式事务）
在方法上加@GlobalTransactional  // Seata全局事务注解


TC（Transaction Coordinator）事务协调者
TM（Transaction Manager）事务管理器
RM（Resource Manager）资源管理器



TC（Transaction Coordinator）事务协调者
  - Seata服务器
  - 维护全局事务和分支事务状态
  - 协调全局事务提交或回滚

TM（Transaction Manager）事务管理器
  - 标注@GlobalTransactional的服务
  - 开启全局事务
  - 提交或回滚全局事务

RM（Resource Manager）资源管理器
  - 每个微服务
  - 管理分支事务
  - 与TC通信，注册分支事务、报告状态

