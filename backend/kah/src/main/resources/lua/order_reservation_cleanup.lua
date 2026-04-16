local restoreCount = tonumber(ARGV[1])
for i = 1, restoreCount * 2, 2 do
    redis.call('ZADD', KEYS[1], ARGV[i + 1], ARGV[i + 2])
end
local reservedHandles = redis.call('ZRANGE', KEYS[2], 0, -1)
for _, handle in ipairs(reservedHandles) do
    redis.call('ZREM', KEYS[5], handle)
end
redis.call('DEL', KEYS[2])
redis.call('DEL', KEYS[3])
redis.call('ZREM', KEYS[4], ARGV[restoreCount * 2 + 2])
return #reservedHandles