local key = KEYS[1]          -- 锁的key
local threadId = ARGV[1]     -- 线程唯一标识
local releaseTime = ARGV[2]  -- 锁的过期时间
-- 判断锁是否存在
if (redis.call('exists', key) == 0) then
    -- 不存在，新建锁
    redis.call('hset', key, threadId, '1')
    redis.call('expire', key, releaseTime)
    return 1
end
-- 存在，判断是否是当前线程
if (redis.call('hexists', key, threadId) == 1) then
    -- 是当前线程，重入
    redis.call('hincrby', key, threadId, '1')
    -- 刷新过期时间
    redis.call('expire', key, releaseTime)
    return 1
end
return 0
