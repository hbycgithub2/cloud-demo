package cn.itcast.seckill.controller;

import cn.itcast.seckill.pojo.Result;
import cn.itcast.seckill.pojo.SeckillDTO;
import cn.itcast.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀Controller
 */
@Slf4j
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 秒杀接口
     */
    @PostMapping("/kill")
    public Result<String> seckill(@RequestBody SeckillDTO dto) {
        String orderNo = seckillService.seckill(dto);

        if (orderNo == null) {
            return Result.fail("秒杀失败，库存不足或已秒杀");
        }

        return Result.success("秒杀成功，请等待支付", orderNo);
    }

    /**
     * 预热库存接口
     */
    @PostMapping("/preload/{productId}")
    public Result<String> preloadStock(@PathVariable Long productId) {
        seckillService.preloadStock(productId);
        return Result.success("库存预热成功");
    }
}
