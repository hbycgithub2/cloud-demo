@echo off
chcp 65001 >nul
echo ========================================
echo 秒杀系统环境检查和修复工具
echo ========================================
echo.

:: ==========================================
:: 第1步：检查Java环境
:: ==========================================
echo [1/5] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Java已安装
    java -version 2>&1 | findstr "version"
) else (
    echo ❌ Java未安装或未配置环境变量
    echo 请安装Java 1.8或更高版本
    echo 下载地址：https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)
echo.

:: ==========================================
:: 第2步：检查Maven环境
:: ==========================================
echo [2/5] 检查Maven环境...
mvn -version >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Maven已安装
    mvn -version 2>&1 | findstr "Apache Maven"
) else (
    echo ❌ Maven未安装或未配置环境变量
    echo 请安装Maven 3.x
    echo 下载地址：https://maven.apache.org/download.cgi
    pause
    exit /b 1
)
echo.

:: ==========================================
:: 第3步：检查MySQL
:: ==========================================
echo [3/5] 检查MySQL...

:: 方式1：尝试直接连接
mysql -uroot -proot -e "SELECT 1" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ MySQL已启动，可以正常连接
    mysql -uroot -proot -e "SELECT VERSION()" 2>&1 | findstr -v "mysql:"
    goto :redis_check
)

:: 方式2：检查MySQL进程
tasklist | findstr /I "mysqld.exe" >nul 2>&1
if %errorlevel% equ 0 (
    echo ⚠️  MySQL进程存在，但无法连接
    echo 可能原因：
    echo   1. 密码不是root
    echo   2. MySQL端口不是3306
    echo   3. MySQL未完全启动
    echo.
    echo 请手动测试连接：
    echo   mysql -uroot -p你的密码
    goto :redis_check
)

:: MySQL未启动
echo ❌ MySQL未启动
echo.
echo 请启动MySQL：
echo   方式1：net start mysql
echo   方式2：启动MySQL服务（services.msc）
echo   方式3：运行MySQL安装目录下的mysqld.exe
echo.
set /p start_mysql="是否尝试启动MySQL服务？(Y/N): "
if /i "%start_mysql%"=="Y" (
    net start mysql
    timeout /t 3 >nul
    mysql -uroot -proot -e "SELECT 1" >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ MySQL启动成功
    ) else (
        echo ❌ MySQL启动失败，请手动启动
    )
)

:redis_check
echo.

:: ==========================================
:: 第4步：检查Redis
:: ==========================================
echo [4/5] 检查Redis...

:: 尝试常见的Redis安装路径
set REDIS_PATHS=^
    "C:\Program Files\Redis\redis-cli.exe" ^
    "C:\Redis\redis-cli.exe" ^
    "D:\Redis\redis-cli.exe" ^
    "D:\Program Files\Redis\redis-cli.exe" ^
    "%USERPROFILE%\Redis\redis-cli.exe"

set REDIS_CLI=
for %%p in (%REDIS_PATHS%) do (
    if exist %%p (
        set REDIS_CLI=%%p
        goto :found_redis_cli
    )
)

:: 检查Redis是否在PATH中
redis-cli --version >nul 2>&1
if %errorlevel% equ 0 (
    set REDIS_CLI=redis-cli
    goto :found_redis_cli
)

:: Redis CLI未找到
echo ❌ Redis CLI未找到
echo.
echo Redis可能的安装位置：
echo   C:\Program Files\Redis\
echo   C:\Redis\
echo   D:\Redis\
echo.
echo 请选择操作：
echo   1. 手动输入Redis安装路径
echo   2. 下载安装Redis
echo   3. 跳过Redis检查
echo.
set /p redis_choice="请选择 (1/2/3): "

if "%redis_choice%"=="1" (
    set /p redis_path="请输入Redis安装目录（例如：C:\Redis）: "
    if exist "%redis_path%\redis-cli.exe" (
        set REDIS_CLI="%redis_path%\redis-cli.exe"
        goto :found_redis_cli
    ) else (
        echo ❌ 路径无效，未找到redis-cli.exe
        goto :rocketmq_check
    )
)

if "%redis_choice%"=="2" (
    echo.
    echo Redis下载地址：
    echo   Windows版本：https://github.com/tporadowski/redis/releases
    echo   或者：https://redis.io/download
    echo.
    echo 下载后解压到任意目录，然后重新运行本脚本
    pause
    exit /b 1
)

goto :rocketmq_check

:found_redis_cli
echo ✅ Redis CLI已找到：%REDIS_CLI%

:: 测试Redis连接
%REDIS_CLI% ping >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Redis已启动，可以正常连接
    %REDIS_CLI% --version 2>&1
    goto :rocketmq_check
)

:: Redis未启动
echo ⚠️  Redis CLI存在，但Redis服务未启动
echo.
echo 请启动Redis：
for %%p in (%REDIS_CLI%) do set REDIS_DIR=%%~dp
echo   方式1：双击运行 %REDIS_DIR%redis-server.exe
echo   方式2：命令行运行 %REDIS_CLI:redis-cli=redis-server%
echo.
set /p start_redis="是否尝试启动Redis服务？(Y/N): "
if /i "%start_redis%"=="Y" (
    start "Redis Server" %REDIS_CLI:redis-cli=redis-server%
    timeout /t 3 >nul
    %REDIS_CLI% ping >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ Redis启动成功
    ) else (
        echo ❌ Redis启动失败，请手动启动
    )
)

:rocketmq_check
echo.

:: ==========================================
:: 第5步：检查RocketMQ
:: ==========================================
echo [5/5] 检查RocketMQ...

:: 检查NameServer端口
netstat -ano | findstr ":9876" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ RocketMQ NameServer已启动（端口9876）
) else (
    echo ❌ RocketMQ NameServer未启动（端口9876）
    echo.
    echo 请启动RocketMQ：
    echo   1. 启动NameServer：
    echo      cd RocketMQ安装目录\bin
    echo      start mqnamesrv.cmd
    echo.
    echo   2. 启动Broker：
    echo      start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
    echo.
    set /p rocketmq_path="请输入RocketMQ安装目录（例如：D:\rocketmq），或按Enter跳过: "
    if not "%rocketmq_path%"=="" (
        if exist "%rocketmq_path%\bin\mqnamesrv.cmd" (
            echo 正在启动RocketMQ NameServer...
            start "RocketMQ NameServer" cmd /k "cd /d %rocketmq_path%\bin && mqnamesrv.cmd"
            timeout /t 5 >nul
            
            echo 正在启动RocketMQ Broker...
            start "RocketMQ Broker" cmd /k "cd /d %rocketmq_path%\bin && mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true"
            timeout /t 5 >nul
            
            netstat -ano | findstr ":9876" >nul 2>&1
            if %errorlevel% equ 0 (
                echo ✅ RocketMQ启动成功
            ) else (
                echo ⚠️  RocketMQ可能正在启动中，请等待30秒后再测试
            )
        ) else (
            echo ❌ 路径无效，未找到mqnamesrv.cmd
        )
    )
)

:: 检查Broker端口
netstat -ano | findstr ":10911" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ RocketMQ Broker已启动（端口10911）
) else (
    echo ⚠️  RocketMQ Broker未启动（端口10911）
)

echo.
echo ========================================
echo 环境检查完成！
echo ========================================
echo.

:: ==========================================
:: 生成环境报告
:: ==========================================
echo 生成环境报告...
(
    echo # 秒杀系统环境检查报告
    echo.
    echo **检查时间：** %date% %time%
    echo.
    echo ## 环境检查结果
    echo.
    echo ### 1. Java环境
    java -version 2>&1
    echo.
    echo ### 2. Maven环境
    mvn -version 2>&1
    echo.
    echo ### 3. MySQL
    mysql -uroot -proot -e "SELECT VERSION()" 2>&1
    echo.
    echo ### 4. Redis
    if defined REDIS_CLI (
        %REDIS_CLI% --version 2>&1
        %REDIS_CLI% ping 2>&1
    ) else (
        echo Redis CLI未找到
    )
    echo.
    echo ### 5. RocketMQ
    netstat -ano | findstr ":9876"
    netstat -ano | findstr ":10911"
    echo.
) > 环境检查报告.txt

echo ✅ 环境报告已生成：环境检查报告.txt
echo.

:: ==========================================
:: 下一步建议
:: ==========================================
echo ========================================
echo 下一步操作建议
echo ========================================
echo.

:: 检查所有服务是否就绪
set ALL_READY=1

java -version >nul 2>&1
if %errorlevel% neq 0 set ALL_READY=0

mvn -version >nul 2>&1
if %errorlevel% neq 0 set ALL_READY=0

mysql -uroot -proot -e "SELECT 1" >nul 2>&1
if %errorlevel% neq 0 set ALL_READY=0

if defined REDIS_CLI (
    %REDIS_CLI% ping >nul 2>&1
    if %errorlevel% neq 0 set ALL_READY=0
) else (
    set ALL_READY=0
)

netstat -ano | findstr ":9876" >nul 2>&1
if %errorlevel% neq 0 set ALL_READY=0

if %ALL_READY% equ 1 (
    echo ✅ 所有环境已就绪！
    echo.
    echo 可以开始测试秒杀系统：
    echo   1. 初始化数据库：mysql -uroot -proot ^< init.sql
    echo   2. 启动服务：双击运行 快速启动脚本.bat
    echo   3. 运行测试：双击运行 测试脚本.bat
    echo.
) else (
    echo ⚠️  部分环境未就绪，请先完成以下操作：
    echo.
    
    java -version >nul 2>&1
    if %errorlevel% neq 0 echo   [ ] 安装Java 1.8+
    
    mvn -version >nul 2>&1
    if %errorlevel% neq 0 echo   [ ] 安装Maven 3.x
    
    mysql -uroot -proot -e "SELECT 1" >nul 2>&1
    if %errorlevel% neq 0 echo   [ ] 启动MySQL
    
    if defined REDIS_CLI (
        %REDIS_CLI% ping >nul 2>&1
        if %errorlevel% neq 0 echo   [ ] 启动Redis
    ) else (
        echo   [ ] 安装Redis
    )
    
    netstat -ano | findstr ":9876" >nul 2>&1
    if %errorlevel% neq 0 echo   [ ] 启动RocketMQ
    
    echo.
    echo 完成后重新运行本脚本检查
)

echo.
echo ========================================
pause
