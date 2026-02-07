第1步：启动NameServer
cd D:\rocketmq\bin
start mqnamesrv.cmd

第2步：启动Broker（带配置文件）
cd D:\rocketmq\bin
start mqbroker.cmd -n 127.0.0.1:9876 -c ../conf/broker.conf autoCreateTopicEnable=true

第3步：验证启动
jps -l


应该看到：

NamesrvStartup
BrokerStartup


第4步：测试
curl -X POST http://localhost:8090/message/send -H "Content-Type: application/json" -d "{\"userId\":1001,\"productName\":\"iPhone 15 Pro\",\"price\":7999.00,\"quantity\":1}"




继续测试场景3：事务消息（最重要）
curl -X POST http://localhost:8090/message/sendTransaction -H "Content-Type: application/json" -d "{\"userId\":1003,\"productName\":\"iPhone 15 Pro\",\"price\":7999.00,\"quantity\":2}"

