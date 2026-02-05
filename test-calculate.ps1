# 测试即时算费接口

Write-Host "==================================" -ForegroundColor Green
Write-Host "测试即时算费服务" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Green
Write-Host ""

# 测试1：健康检查
Write-Host "【测试1】健康检查接口 GET /calculate/test" -ForegroundColor Cyan
try {
    $result1 = Invoke-RestMethod -Uri http://localhost:8083/calculate/test -Method Get
    Write-Host "✅ 成功！" -ForegroundColor Green
    $result1 | ConvertTo-Json
} catch {
    Write-Host "❌ 失败：$_" -ForegroundColor Red
}

Write-Host ""
Write-Host "==================================" -ForegroundColor Green
Write-Host ""

# 测试2：即时算费
Write-Host "【测试2】即时算费接口 POST /calculate/realtime" -ForegroundColor Cyan
Write-Host "请求参数：kindCode=050200, amount=100000" -ForegroundColor Yellow

$body = @{
    kindCode = "050200"
    amount = 100000
} | ConvertTo-Json

try {
    $result2 = Invoke-RestMethod -Uri http://localhost:8083/calculate/realtime -Method Post -ContentType "application/json" -Body $body
    Write-Host "✅ 成功！" -ForegroundColor Green
    Write-Host ""
    Write-Host "计算结果：" -ForegroundColor Cyan
    Write-Host "  险种代码：$($result2.kindCode)" -ForegroundColor White
    Write-Host "  保额：$($result2.amount) 元" -ForegroundColor White
    Write-Host "  保费：$($result2.premium) 元" -ForegroundColor Yellow
    Write-Host "  基础费率：$($result2.rate)" -ForegroundColor White
    Write-Host "  折扣系数：$($result2.discount)" -ForegroundColor White
    Write-Host "  NCD系数：$($result2.ncdRate)" -ForegroundColor White
    Write-Host ""
    Write-Host "完整响应：" -ForegroundColor Cyan
    $result2 | ConvertTo-Json
} catch {
    Write-Host "❌ 失败：$_" -ForegroundColor Red
}

Write-Host ""
Write-Host "==================================" -ForegroundColor Green
Write-Host "测试完成！" -ForegroundColor Green
Write-Host "==================================" -ForegroundColor Green
