@echo off
chcp 65001 >nul
echo ========================================
echo 秒杀系统快速启动脚本
echo ========================================
echo.

echo [1/5] 检查MySQL是否启动...
mysql -uroot -proot -e "SELECT 1" >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ MySQL未启动，请先启动MySQL
    pause
    exit /b 1
)
echo ✅ MySQL已启动

echo.
echo [2/5] 检查Redis是否启动...
redis-cli ping >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Redis未启动，请先启动Redis
    pause
    exit /b 1
)
echo ✅ Redis已启动

echo.
echo [3/5] 检查RocketMQ是否启动...
netstat -ano | findstr ":9876" >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ RocketMQ未启动，请先启动RocketMQ
    pause
    exit /b 1
)
echo ✅ RocketMQ已启动

echo.
echo [4/5] 初始化数据库...
mysql -uroot -proot < init.sql
if %errorlevel% neq 0 (
    echo ❌ 数据库初始化失败
    pause
    exit /b 1
)
echo ✅ 数据库初始化成功

echo.
echo [5/5] 启动服务...
echo.
echo 正在启动 seckill-service (端口8092)...
start "seckill-service" cmd /k "cd seckill-service && mvn spring-boot:run"
timeout /t 5 >nul

echo 正在启动 seckill-consumer (端口8093)...
start "seckill-consumer" cmd /k "cd seckill-consumer && mvn spring-boot:run"

echo.
echo ========================================
echo ✅ 启动完成！
echo ========================================
echo.
echo 服务地址：
echo   - seckill-service: http://localhost:8092
echo   - seckill-consumer: http://localhost:8093
echo.
echo 测试接口：
echo   - 预热库存: POST http://localhost:8092/seckill/preload/1
echo   - 秒杀接口: POST http://localhost:8092/seckill/kill
echo.
echo 按任意键退出...
pause >nul
