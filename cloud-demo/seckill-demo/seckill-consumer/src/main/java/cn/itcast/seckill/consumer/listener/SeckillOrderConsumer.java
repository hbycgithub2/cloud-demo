package cn.itcast.seckill.consumer.listener;

import cn.itcast.seckill.consumer.mapper.SeckillOrderMapper;
import cn.itcast.seckill.consumer.mapper.SeckillProductMapper;
import cn.itcast.seckill.consumer.mapper.StockLogMapper;
import cn.itcast.seckill.consumer.mapper.UserSeckillMapper;
import cn.itcast.seckill.pojo.SeckillDTO;
import cn.itcast.seckill.pojo.SeckillOrder;
import cn.itcast.seckill.pojo.SeckillProduct;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 秒杀订单消费者 - 异步创建订单、扣减MySQL库存
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "seckill-topic",
    consumerGroup = "seckill-consumer"
)
public class SeckillOrderConsumer implements RocketMQListener<SeckillDTO> {

    @Autowired
    private SeckillOrderMapper orderMapper;

    @Autowired
    private SeckillProductMapper productMapper;

    @Autowired
    private StockLogMapper stockLogMapper;

    @Autowired
    private UserSeckillMapper userSeckillMapper;

    @Override
    public void onMessage(SeckillDTO dto) {
        log.info("========== 秒杀订单消费 ==========");
        log.info("收到秒杀消息：用户ID={}, 商品ID={}, 数量={}",
                dto.getUserId(), dto.getProductId(), dto.getQuantity());

        try {
            // 第1步：查询商品信息
            SeckillProduct product = productMapper.selectById(dto.getProductId());
            if (product == null) {
                log.error("商品不存在：{}", dto.getProductId());
                return;
            }

            // 第2步：扣减MySQL库存（乐观锁）
            int rows = productMapper.reduceStock(dto.getProductId(), dto.getQuantity());
            if (rows == 0) {
                log.error("MySQL库存不足，扣减失败：商品ID={}", dto.getProductId());
                // TODO: 回滚Redis库存
                return;
            }

            log.info("MySQL库存扣减成功：商品ID={}, 数量={}", dto.getProductId(), dto.getQuantity());

            // 第3步：创建订单
            SeckillOrder order = new SeckillOrder();
            order.setOrderNo(generateOrderNo(dto.getUserId(), dto.getProductId()));
            order.setUserId(dto.getUserId());
            order.setProductId(dto.getProductId());
            order.setProductName(product.getProductName());
            order.setPrice(product.getPrice());
            order.setQuantity(dto.getQuantity());
            order.setStatus(0);  // 0=待支付
            order.setCreateTime(LocalDateTime.now());

            orderMapper.insert(order);
            log.info("订单创建成功：订单号={}", order.getOrderNo());

            // 第4步：记录库存扣减日志
            stockLogMapper.insert(
                    order.getOrderNo(),
                    dto.getProductId(),
                    product.getProductName(),
                    dto.getQuantity(),
                    product.getStock() + dto.getQuantity(),  // 扣减前库存
                    product.getStock(),                       // 扣减后库存
                    1                                         // 1=扣减
            );

            // 第5步：记录用户秒杀记录
            userSeckillMapper.insert(dto.getUserId(), dto.getProductId(), order.getOrderNo());

            log.info("秒杀订单处理完成：订单号={}", order.getOrderNo());

        } catch (Exception e) {
            log.error("秒杀订单处理失败：{}", e.getMessage(), e);
            // TODO: 回滚Redis库存
        }

        log.info("==================================");
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo(Long userId, Long productId) {
        return "SK" + System.currentTimeMillis() + userId + productId;
    }
}
