local added = 0
for index = 1, #ARGV, 2 do
    local handle = ARGV[index]
    local score = tonumber(ARGV[index + 1])
    if handle and score and redis.call('ZSCORE', KEYS[2], handle) == false then
        added = added + redis.call('ZADD', KEYS[1], 'NX', score, handle)
    end
end
return added
