-- Redis Lua脚本：原子扣减库存
-- KEYS[1]: 库存key，格式：seckill:stock:productId
-- ARGV[1]: 扣减数量

-- 获取当前库存
local stock = redis.call('GET', KEYS[1])

-- 如果库存不存在，返回-1
if not stock then
    return -1
end

-- 转换为数字
stock = tonumber(stock)

-- 如果库存不足，返回0
if stock < tonumber(ARGV[1]) then
    return 0
end

-- 扣减库存
redis.call('DECRBY', KEYS[1], ARGV[1])

-- 返回扣减后的库存
return stock - tonumber(ARGV[1])
