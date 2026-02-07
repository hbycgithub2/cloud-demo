@echo off
chcp 65001 >nul
echo ========================================
echo 查找Redis安装位置
echo ========================================
echo.

echo 正在搜索Redis安装位置，请稍候...
echo.

:: ==========================================
:: 方法1：检查常见安装路径
:: ==========================================
echo [方法1] 检查常见安装路径...
echo.

set FOUND=0

:: 检查C盘常见路径
if exist "C:\Redis\redis-server.exe" (
    echo ✅ 找到：C:\Redis\
    set FOUND=1
)

if exist "C:\Program Files\Redis\redis-server.exe" (
    echo ✅ 找到：C:\Program Files\Redis\
    set FOUND=1
)

if exist "C:\Program Files (x86)\Redis\redis-server.exe" (
    echo ✅ 找到：C:\Program Files ^(x86^)\Redis\
    set FOUND=1
)

:: 检查D盘常见路径
if exist "D:\Redis\redis-server.exe" (
    echo ✅ 找到：D:\Redis\
    set FOUND=1
)

if exist "D:\Program Files\Redis\redis-server.exe" (
    echo ✅ 找到：D:\Program Files\Redis\
    set FOUND=1
)

:: 检查用户目录
if exist "%USERPROFILE%\Redis\redis-server.exe" (
    echo ✅ 找到：%USERPROFILE%\Redis\
    set FOUND=1
)

if exist "%USERPROFILE%\Downloads\Redis\redis-server.exe" (
    echo ✅ 找到：%USERPROFILE%\Downloads\Redis\
    set FOUND=1
)

if exist "%USERPROFILE%\Desktop\Redis\redis-server.exe" (
    echo ✅ 找到：%USERPROFILE%\Desktop\Redis\
    set FOUND=1
)

if %FOUND% equ 0 (
    echo ⚠️  常见路径中未找到Redis
)

echo.

:: ==========================================
:: 方法2：检查Redis进程
:: ==========================================
echo [方法2] 检查Redis进程...
echo.

tasklist /FI "IMAGENAME eq redis-server.exe" 2>nul | find /I "redis-server.exe" >nul
if %errorlevel% equ 0 (
    echo ✅ Redis进程正在运行
    echo.
    echo 进程详情：
    wmic process where "name='redis-server.exe'" get ExecutablePath 2>nul
    echo.
) else (
    echo ⚠️  Redis进程未运行
)

echo.

:: ==========================================
:: 方法3：搜索整个C盘和D盘
:: ==========================================
echo [方法3] 搜索整个磁盘（可能需要几分钟）...
echo.
set /p search_all="是否搜索整个C盘和D盘？这可能需要5-10分钟 (Y/N): "

if /i "%search_all%"=="Y" (
    echo.
    echo 正在搜索C盘...
    dir /s /b C:\redis-server.exe 2>nul
    
    echo.
    echo 正在搜索D盘...
    dir /s /b D:\redis-server.exe 2>nul
    
    echo.
    echo 搜索完成！
)

echo.

:: ==========================================
:: 方法4：检查环境变量PATH
:: ==========================================
echo [方法4] 检查环境变量PATH...
echo.

where redis-server.exe 2>nul
if %errorlevel% equ 0 (
    echo ✅ Redis已配置到环境变量PATH中
) else (
    echo ⚠️  Redis未配置到环境变量PATH中
)

echo.

where redis-cli.exe 2>nul
if %errorlevel% equ 0 (
    echo ✅ Redis CLI已配置到环境变量PATH中
) else (
    echo ⚠️  Redis CLI未配置到环境变量PATH中
)

echo.

:: ==========================================
:: 方法5：检查注册表
:: ==========================================
echo [方法5] 检查注册表...
echo.

reg query "HKLM\SOFTWARE\Redis" /s 2>nul
if %errorlevel% equ 0 (
    echo ✅ 在注册表中找到Redis信息
) else (
    echo ⚠️  注册表中未找到Redis信息
)

echo.

:: ==========================================
:: 方法6：检查Windows服务
:: ==========================================
echo [方法6] 检查Windows服务...
echo.

sc query Redis 2>nul | find "STATE" >nul
if %errorlevel% equ 0 (
    echo ✅ Redis已安装为Windows服务
    sc query Redis
) else (
    echo ⚠️  Redis未安装为Windows服务
)

echo.

:: ==========================================
:: 总结和建议
:: ==========================================
echo ========================================
echo 搜索完成！
echo ========================================
echo.

if %FOUND% equ 1 (
    echo ✅ 已找到Redis安装位置（见上方）
    echo.
    echo 下一步操作：
    echo   1. 记住Redis安装路径
    echo   2. 运行 环境检查和修复.bat
    echo   3. 输入Redis安装路径
) else (
    echo ⚠️  未找到Redis安装位置
    echo.
    echo 可能的原因：
    echo   1. Redis安装在其他盘符（E盘、F盘等）
    echo   2. Redis文件夹名称不是"Redis"
    echo   3. Redis未正确安装
    echo.
    echo 建议操作：
    echo   1. 回忆Redis下载后解压到哪里
    echo   2. 搜索文件名：redis-server.exe
    echo   3. 重新下载安装Redis
    echo.
    echo Redis下载地址：
    echo   https://github.com/tporadowski/redis/releases
)

echo.
echo ========================================
echo 手动搜索方法
echo ========================================
echo.
echo 如果上述方法都没找到，请尝试：
echo.
echo 1. 打开文件资源管理器
echo 2. 在搜索框输入：redis-server.exe
echo 3. 等待搜索完成
echo 4. 右键点击找到的文件 → 打开文件位置
echo.
echo 或者：
echo.
echo 1. 按Win+R打开运行
echo 2. 输入：cmd
echo 3. 输入：where /R C:\ redis-server.exe
echo 4. 等待搜索完成（可能需要几分钟）
echo.

pause
