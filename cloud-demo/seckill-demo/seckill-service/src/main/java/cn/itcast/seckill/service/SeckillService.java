package cn.itcast.seckill.service;

import cn.itcast.seckill.mapper.SeckillProductMapper;
import cn.itcast.seckill.pojo.SeckillDTO;
import cn.itcast.seckill.pojo.SeckillProduct;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 秒杀服务
 */
@Slf4j
@Service
public class SeckillService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DefaultRedisScript<Long> stockScript;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private SeckillProductMapper productMapper;

    /**
     * 秒杀接口
     */
    public String seckill(SeckillDTO dto) {
        Long userId = dto.getUserId();
        Long productId = dto.getProductId();
        Integer quantity = dto.getQuantity();

        log.info("========== 秒杀请求 ==========");
        log.info("用户ID：{}，商品ID：{}，数量：{}", userId, productId, quantity);

        // 第1步：检查用户是否已秒杀（防止重复秒杀）
        String userKey = "seckill:user:" + userId + ":" + productId;
        Boolean hasKey = redisTemplate.hasKey(userKey);
        if (Boolean.TRUE.equals(hasKey)) {
            log.warn("用户{}已秒杀商品{}，不能重复秒杀", userId, productId);
            return null;
        }

        // 第2步：Redis扣减库存（Lua脚本，原子操作）
        String stockKey = "seckill:stock:" + productId;
        Long result = redisTemplate.execute(
                stockScript,
                Collections.singletonList(stockKey),
                quantity
        );

        log.info("Redis扣减库存结果：{}", result);

        // 第3步：判断扣减结果
        if (result == null || result == -1) {
            log.error("商品{}库存不存在", productId);
            return null;
        }

        if (result == 0) {
            log.warn("商品{}库存不足", productId);
            return null;
        }

        // 第4步：扣减成功，标记用户已秒杀
        redisTemplate.opsForValue().set(userKey, "1");

        // 第5步：发送MQ消息（异步创建订单）
        rocketMQTemplate.convertAndSend("seckill-topic", dto);
        log.info("秒杀成功，发送MQ消息");

        // 第6步：生成订单号并返回
        String orderNo = generateOrderNo(userId, productId);
        log.info("秒杀成功，订单号：{}", orderNo);
        log.info("==================================");

        return orderNo;
    }

    /**
     * 预热库存到Redis
     */
    public void preloadStock(Long productId) {
        // 查询MySQL库存
        SeckillProduct product = productMapper.selectById(productId);
        if (product == null) {
            log.error("商品{}不存在", productId);
            return;
        }

        // 预热到Redis
        String stockKey = "seckill:stock:" + productId;
        redisTemplate.opsForValue().set(stockKey, product.getStock());
        log.info("预热库存成功：商品ID={}，库存={}", productId, product.getStock());
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo(Long userId, Long productId) {
        return "SK" + System.currentTimeMillis() + userId + productId;
    }
}
