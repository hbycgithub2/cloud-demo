package cn.itcast.rocketmq.consumer.consumer;

import cn.itcast.rocketmq.pojo.Coupon;
import cn.itcast.rocketmq.consumer.mapper.CouponMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场景5：批量消息消费者 - 批量发送优惠券
 */
@Slf4j
@Service
@RocketMQMessageListener(
    topic = "coupon-topic",
    consumerGroup = "coupon-consumer"
)
public class CouponConsumer implements RocketMQListener<Long> {

    @Autowired
    private CouponMapper couponMapper;

    @Override
    public void onMessage(Long userId) {
        log.info("========== 批量消息消费 ==========");
        log.info("收到优惠券发放消息：用户ID={}", userId);

        // 创建优惠券
        Coupon coupon = new Coupon();
        coupon.setUserId(userId);
        coupon.setCouponName("新用户专享券");
        coupon.setAmount(new BigDecimal("50.00"));
        coupon.setStatus(0);  // 未使用
        coupon.setExpireTime(LocalDateTime.now().plusDays(30));  // 30天后过期

        // 保存优惠券
        couponMapper.insert(coupon);
        log.info("优惠券发放成功：用户ID={}, 优惠券={}, 金额={}", userId, coupon.getCouponName(), coupon.getAmount());
        log.info("==================================");
    }
}
