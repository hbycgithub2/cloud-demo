@echo off
chcp 65001 >nul
echo ========================================
echo 秒杀系统测试脚本
echo ========================================
echo.

echo [测试1] 预热库存（商品ID=1）
curl -X POST http://localhost:8092/seckill/preload/1
echo.
echo.

timeout /t 2 >nul

echo [测试2] 秒杀请求（用户1001秒杀商品1）
curl -X POST http://localhost:8092/seckill/kill ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\":1001,\"productId\":1}"
echo.
echo.

timeout /t 2 >nul

echo [测试3] 重复秒杀（应该失败）
curl -X POST http://localhost:8092/seckill/kill ^
  -H "Content-Type: application/json" ^
  -d "{\"userId\":1001,\"productId\":1}"
echo.
echo.

timeout /t 2 >nul

echo [测试4] 查询Redis库存
redis-cli GET seckill:stock:1
echo.

echo [测试5] 查询MySQL订单数量
mysql -uroot -proot cloud_seckill -e "SELECT COUNT(*) AS 订单数 FROM tb_seckill_order"
echo.

echo [测试6] 查询MySQL库存
mysql -uroot -proot cloud_seckill -e "SELECT product_name, stock FROM tb_seckill_product WHERE id=1"
echo.

echo ========================================
echo ✅ 测试完成！
echo ========================================
pause
