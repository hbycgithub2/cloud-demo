package cn.itcast.calculate.controller;

import cn.itcast.calculate.pojo.CalculateRequest;
import cn.itcast.calculate.pojo.CalculateResponse;
import cn.itcast.calculate.service.CalculateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 即时算费Controller
 * 
 * @author demo
 * @date 2026-02-05
 */
@Slf4j
@RestController
@RequestMapping("/calculate")
public class CalculateController {
    
    @Autowired
    private CalculateService calculateService;
    
    /**
     * 测试接口 - GET方式
     * 
     * 访问：http://localhost:8083/calculate/test
     */
    @GetMapping("/test")
    public Map<String, Object> test() {
        log.info("测试接口被调用");
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "即时算费服务正常运行");
        result.put("service", "calculate-service");
        result.put("port", 8083);
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }
    
    /**
     * 即时算费接口 - POST方式
     * 
     * 请求示例：
     * {
     *   "kindCode": "050200",
     *   "amount": 100000
     * }
     * 
     * 访问：http://localhost:8083/calculate/realtime
     */
    @PostMapping("/realtime")
    public CalculateResponse realTimeCalculate(@RequestBody CalculateRequest request) {
        log.info("即时算费接口被调用，请求参数：{}", request);
        
        return calculateService.calculate(request);
    }
}
