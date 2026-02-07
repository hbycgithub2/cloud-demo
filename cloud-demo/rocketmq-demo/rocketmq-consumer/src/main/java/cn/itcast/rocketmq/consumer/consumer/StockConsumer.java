package cn.itcast.rocketmq.consumer.consumer;

import cn.itcast.rocketmq.pojo.Order;
import cn.itcast.rocketmq.pojo.Stock;
import cn.itcast.rocketmq.pojo.StockLog;
import cn.itcast.rocketmq.consumer.mapper.StockMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 场景3：事务消息消费者 - 扣减库存
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "stock-topic",
    consumerGroup = "stock-consumer"
)
public class StockConsumer implements RocketMQListener<Order> {

    @Autowired
    private StockMapper stockMapper;

    @Override
    public void onMessage(Order order) {
        log.info("========== 事务消息消费 ==========");
        log.info("收到订单消息：订单号={}, 商品={}, 数量={}", 
                 order.getOrderNo(), order.getProductName(), order.getQuantity());

        try {
            // 1. 查询当前库存
            Stock stock = stockMapper.selectByProductName(order.getProductName());
            if (stock == null) {
                log.error("商品不存在：{}", order.getProductName());
                return;
            }

            log.info("当前库存：商品={}, 库存={}", stock.getProductName(), stock.getStock());

            // 2. 扣减库存（乐观锁）
            int rows = stockMapper.deductStock(order.getProductName(), order.getQuantity(), stock.getVersion());
            if (rows == 0) {
                log.error("库存扣减失败（库存不足或版本冲突）：商品={}, 需要={}, 当前={}", 
                         order.getProductName(), order.getQuantity(), stock.getStock());
                return;
            }

            // 3. 记录库存扣减日志
            StockLog stockLog = new StockLog();
            stockLog.setOrderNo(order.getOrderNo());
            stockLog.setProductName(order.getProductName());
            stockLog.setQuantity(order.getQuantity());
            stockLog.setBeforeStock(stock.getStock());
            stockLog.setAfterStock(stock.getStock() - order.getQuantity());
            stockMapper.insertLog(stockLog);

            log.info("库存扣减成功：商品={}, 扣减数量={}, 剩余库存={}", 
                     order.getProductName(), order.getQuantity(), stock.getStock() - order.getQuantity());
        } catch (Exception e) {
            log.error("库存扣减异常", e);
        }
        log.info("==================================");
    }
}
