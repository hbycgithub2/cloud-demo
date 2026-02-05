@echo off
chcp 65001 >nul
echo ========================================
echo Git提交脚本
echo ========================================
echo.

echo [1/5] 查看当前Git状态...
git status
echo.

echo [2/5] 添加所有更改到暂存区...
git add .
echo ✅ 已添加所有更改
echo.

echo [3/5] 再次查看状态（确认要提交的文件）...
git status
echo.

echo [4/5] 提交更改...
git commit -m "feat: 添加即时算费服务(calculate-service)和配置文件

- 新增 calculate-service 模块（完整的三层架构）
- 添加 .gitignore 排除不必要的文件
- 添加测试脚本和清理脚本
- 端口：8083
- 功能：车险即时算费接口"

if errorlevel 1 (
    echo ❌ 提交失败！
    pause
    exit /b 1
)

echo ✅ 提交成功！
echo.

echo [5/5] 推送到远程仓库...
echo 提示：如果没有配置远程仓库，这一步会失败（可以忽略）
git push
if errorlevel 1 (
    echo ⚠️  推送失败或未配置远程仓库
    echo.
    echo 如果需要推送到远程仓库，请手动执行：
    echo   git remote add origin [你的仓库地址]
    echo   git push -u origin master
) else (
    echo ✅ 推送成功！
)

echo.
echo ========================================
echo 完成！
echo ========================================
echo.
echo 提交信息：
echo   - 新增即时算费服务模块
echo   - 添加配置和测试文件
echo   - 排除不必要的文件
echo.
pause
