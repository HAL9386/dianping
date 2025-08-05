local voucherId = ARGV[1]  -- 商品id
local userId = ARGV[2]     -- 用户id
local orderId = ARGV[3]    -- 订单id
local stockKey = 'seckill:stock:' .. voucherId  -- 库存key
local orderKey = 'seckill:order:' .. voucherId  -- 订单key

-- 判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足
    return 1
end
-- 判断用户是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 用户已下单
    return 2
end
-- 将用户id添加到已下单的集合中
redis.call('sadd', orderKey, userId)
-- 库存减一
redis.call('decr', stockKey)
-- 将订单信息添加到订单队列中
redis.call('xadd', 'stream.orders', '*', 'id', orderId, 'userId', userId, 'voucherId', voucherId)
return 0
