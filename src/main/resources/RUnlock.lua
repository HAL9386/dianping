local key = KEYS[1]          -- 锁的key
local threadId = ARGV[1]     -- 线程唯一标识
local releaseTime = ARGV[2]  -- 锁的过期时间
-- 判断锁是否是自己持有
if (redis.call('hexists', key, threadId) == 0) then
    -- 不是自己持有，返回0
    return nil
end
-- 是自己的锁，重入次数减一
local count = redis.call('hincrby', key, threadId, '-1')
-- 判断重入次数是否为0
if (count == 0) then
    -- 重入次数为0，删除锁
    redis.call('del', key)
end
-- 重入次数大于0
if (count > 0) then
    -- 刷新过期时间
    redis.call('expire', key, releaseTime)
end
return nil