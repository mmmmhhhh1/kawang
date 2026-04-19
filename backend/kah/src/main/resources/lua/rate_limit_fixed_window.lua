local limit = tonumber(ARGV[1])
local windowMillis = tonumber(ARGV[2])
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if current >= limit then
    return 0
end
current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('PEXPIRE', KEYS[1], windowMillis)
end
return 1
