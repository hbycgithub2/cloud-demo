@echo off
chcp 65001 >nul
echo ========================================
echo 秒杀系统 - 编译项目
echo ========================================
echo.

cd /d D:\code\cloud-demo\cloud-demo

echo [1/3] 清理旧的编译文件...
call mvn clean -DskipTests

echo.
echo [2/3] 编译整个项目（包含seckill-common）...
call mvn install -DskipTests -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8

echo.
echo [3/3] 检查编译结果...
if exist "seckill-demo\seckill-common\target\seckill-common-1.0.jar" (
    echo ✅ seckill-common 编译成功
) else (
    echo ❌ seckill-common 编译失败
    pause
    exit /b 1
)

if exist "seckill-demo\seckill-service\target\seckill-service-1.0.jar" (
    echo ✅ seckill-service 编译成功
) else (
    echo ❌ seckill-service 编译失败
    pause
    exit /b 1
)

if exist "seckill-demo\seckill-consumer\target\seckill-consumer-1.0.jar" (
    echo ✅ seckill-consumer 编译成功
) else (
    echo ❌ seckill-consumer 编译失败
    pause
    exit /b 1
)

echo.
echo ========================================
echo ✅ 编译完成！可以启动服务了
echo ========================================
echo.
echo 下一步：运行 启动服务.bat
pause
