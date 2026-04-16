local quantity = tonumber(ARGV[1])
local expiresAt = ARGV[2]
local ttlMillis = tonumber(ARGV[3])
local entries = redis.call('ZRANGE', KEYS[1], 0, quantity - 1, 'WITHSCORES')
if #entries < quantity * 2 then
    return {}
end
for i = 1, #entries, 2 do
    redis.call('ZREM', KEYS[1], entries[i])
    redis.call('ZADD', KEYS[2], entries[i + 1], entries[i])
    redis.call('ZADD', KEYS[5], entries[i + 1], entries[i])
end
redis.call('HSET', KEYS[3], 'productId', ARGV[4], 'userId', ARGV[5], 'expiresAt', expiresAt)
redis.call('PEXPIRE', KEYS[2], ttlMillis)
redis.call('PEXPIRE', KEYS[3], ttlMillis)
redis.call('ZADD', KEYS[4], expiresAt, ARGV[6])
return entries