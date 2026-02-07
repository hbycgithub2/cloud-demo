package cn.itcast.rocketmq.producer.controller;

import cn.itcast.rocketmq.producer.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息发送Controller
 */
@Slf4j
@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private MessageService messageService;

    /**
     * 场景1：普通消息 - 发送短信通知
     * POST http://localhost:8090/message/send
     * {
     *   "userId": 1001,
     *   "productName": "iPhone 15 Pro",
     *   "price": 7999.00,
     *   "quantity": 1
     * }
     */
    @PostMapping("/send")
    public Map<String, Object> sendNormalMessage(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String productName = params.get("productName").toString();
        BigDecimal price = new BigDecimal(params.get("price").toString());
        Integer quantity = Integer.valueOf(params.get("quantity").toString());

        String orderNo = messageService.sendNormalMessage(userId, productName, price, quantity);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "普通消息发送成功");
        result.put("orderNo", orderNo);
        return result;
    }

    /**
     * 场景2：延迟消息 - 自动取消超时订单
     * POST http://localhost:8090/message/sendDelay
     * {
     *   "userId": 1002,
     *   "productName": "MacBook Pro",
     *   "price": 12999.00,
     *   "quantity": 1
     * }
     */
    @PostMapping("/sendDelay")
    public Map<String, Object> sendDelayMessage(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String productName = params.get("productName").toString();
        BigDecimal price = new BigDecimal(params.get("price").toString());
        Integer quantity = Integer.valueOf(params.get("quantity").toString());

        String orderNo = messageService.sendDelayMessage(userId, productName, price, quantity);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "延迟消息发送成功，30分钟后自动取消");
        result.put("orderNo", orderNo);
        return result;
    }

    /**
     * 场景3：事务消息 - 订单和库存一致性
     * POST http://localhost:8090/message/sendTransaction
     * {
     *   "userId": 1003,
     *   "productName": "iPhone 15 Pro",
     *   "price": 7999.00,
     *   "quantity": 2
     * }
     */
    @PostMapping("/sendTransaction")
    public Map<String, Object> sendTransactionMessage(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String productName = params.get("productName").toString();
        BigDecimal price = new BigDecimal(params.get("price").toString());
        Integer quantity = Integer.valueOf(params.get("quantity").toString());

        String orderNo = messageService.sendTransactionMessage(userId, productName, price, quantity);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "事务消息发送成功");
        result.put("orderNo", orderNo);
        return result;
    }

    /**
     * 场景4：顺序消息 - 订单状态变更
     * POST http://localhost:8090/message/sendOrderly
     * {
     *   "orderNo": "ORD20260207123456789",
     *   "status": 1
     * }
     */
    @PostMapping("/sendOrderly")
    public Map<String, Object> sendOrderlyMessage(@RequestBody Map<String, Object> params) {
        String orderNo = params.get("orderNo").toString();
        Integer status = Integer.valueOf(params.get("status").toString());

        String result = messageService.sendOrderlyMessage(orderNo, status);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", result);
        return response;
    }

    /**
     * 场景5：批量消息 - 批量发送优惠券
     * POST http://localhost:8090/message/sendBatch
     * {
     *   "userIds": [1001, 1002, 1003, 1004, 1005]
     * }
     */
    @PostMapping("/sendBatch")
    public Map<String, Object> sendBatchMessage(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Integer> userIdList = (List<Integer>) params.get("userIds");
        List<Long> userIds = new ArrayList<>();
        for (Integer userId : userIdList) {
            userIds.add(userId.longValue());
        }

        String result = messageService.sendBatchMessage(userIds);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", result);
        return response;
    }

    /**
     * 场景6：消息过滤 - 只处理VIP订单
     * POST http://localhost:8090/message/sendVip
     * {
     *   "userId": 1006,
     *   "productName": "AirPods Pro",
     *   "price": 1999.00,
     *   "quantity": 1
     * }
     */
    @PostMapping("/sendVip")
    public Map<String, Object> sendVipMessage(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String productName = params.get("productName").toString();
        BigDecimal price = new BigDecimal(params.get("price").toString());
        Integer quantity = Integer.valueOf(params.get("quantity").toString());

        String orderNo = messageService.sendVipMessage(userId, productName, price, quantity);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "VIP消息发送成功");
        result.put("orderNo", orderNo);
        return result;
    }
}
