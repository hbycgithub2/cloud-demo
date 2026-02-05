@echo off
echo ========================================
echo 清理Git缓存中的不必要文件
echo ========================================
echo.

echo [1/4] 移除 .idea 目录...
git rm -r --cached .idea
if errorlevel 1 (
    echo .idea 目录不在Git跟踪中或已移除
)

echo.
echo [2/4] 移除 target 目录...
git rm -r --cached cloud-demo/*/target
if errorlevel 1 (
    echo target 目录不在Git跟踪中或已移除
)

echo.
echo [3/4] 移除 .iml 文件...
git rm --cached *.iml
git rm --cached cloud-demo/*.iml
git rm --cached cloud-demo/*/*.iml
if errorlevel 1 (
    echo .iml 文件不在Git跟踪中或已移除
)

echo.
echo [4/4] 移除 .windsurfrules 文件...
git rm --cached .windsurfrules
if errorlevel 1 (
    echo .windsurfrules 文件不在Git跟踪中或已移除
)

echo.
echo ========================================
echo 清理完成！
echo ========================================
echo.
echo 现在查看Git状态：
git status --short

echo.
echo 提示：
echo 1. 这些文件已从Git跟踪中移除，但本地文件仍然保留
echo 2. 请检查 git status 确认要提交的更改
echo 3. 然后执行：git commit -m "Remove unnecessary files from Git"
echo.
pause
