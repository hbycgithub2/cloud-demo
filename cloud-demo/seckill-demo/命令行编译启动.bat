@echo off
echo ========================================
echo 秒杀系统 - 命令行编译和启动
echo ========================================

echo.
echo [1/3] 清理并编译项目...
cd /d D:\code\cloud-demo\cloud-demo
call mvn clean install -DskipTests -pl seckill-demo/seckill-common,seckill-demo/seckill-service,seckill-demo/seckill-consumer -am

if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    exit /b 1
)

echo.
echo [2/3] 启动 seckill-service (端口8092)...
start "seckill-service" cmd /k "cd /d D:\code\cloud-demo\cloud-demo\seckill-demo\seckill-service && mvn spring-boot:run"

echo.
echo 等待5秒...
timeout /t 5 /nobreak

echo.
echo [3/3] 启动 seckill-consumer (端口8093)...
start "seckill-consumer" cmd /k "cd /d D:\code\cloud-demo\cloud-demo\seckill-demo\seckill-consumer && mvn spring-boot:run"

echo.
echo ========================================
echo 启动完成！
echo seckill-service: http://localhost:8092
echo seckill-consumer: http://localhost:8093
echo ========================================
pause
