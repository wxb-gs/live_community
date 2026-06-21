-- batch_status.lua
-- 批量查询用户对多个目标的互动状态和计数
--
-- 对于每个 targetId，执行:
--   SISMEMBER {prefix}:users:{targetType}:{targetId} userId
--   GET {prefix}:count:{targetType}:{targetId}
--
-- 参数通过 KEYS 传递: 每个 targetId 对应两个 key
--   KEYS[i*2]   = users key
--   KEYS[i*2+1] = count key
-- KEYS total = targetIds * 2
-- ARGV[1] = userId
--
-- Returns: flat array of [active1, count1, active2, count2, ...]

local userId = ARGV[1]
local results = {}

for i = 1, #KEYS, 2 do
    local usersKey = KEYS[i]
    local countKey = KEYS[i + 1]

    local isMember = redis.call('SISMEMBER', usersKey, userId)
    local count = redis.call('GET', countKey)

    results[#results + 1] = isMember    -- 0 or 1
    results[#results + 1] = tonumber(count) or 0
end

return results
