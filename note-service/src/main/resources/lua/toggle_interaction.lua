-- toggle_interaction.lua
-- 原子操作：检查用户是否已互动 → 添加/移除 → 更新计数器
--
-- KEYS[1] = {prefix}:count:{targetType}:{targetId}    (计数器)
-- KEYS[2] = {prefix}:users:{targetType}:{targetId}    (互动用户集合)
-- KEYS[3] = user:{prefix}:{userId}:{targetType}       (用户互动历史)
-- ARGV[1] = userId
-- ARGV[2] = activeAction   (e.g. "liked", "favorited")
-- ARGV[3] = inactiveAction (e.g. "unliked", "unfavorited")
--
-- Returns: {count (number), action (string)}

local countKey = KEYS[1]
local usersKey = KEYS[2]
local userHistoryKey = KEYS[3]
local userId = ARGV[1]
local activeAction = ARGV[2]
local inactiveAction = ARGV[3]

local isMember = redis.call('SISMEMBER', usersKey, userId)

if isMember == 1 then
    -- 取消互动
    redis.call('SREM', usersKey, userId)
    redis.call('SREM', userHistoryKey, countKey)
    local count = redis.call('DECR', countKey)
    if count < 0 then
        redis.call('SET', countKey, 0)
        count = 0
    end
    return {count, inactiveAction}
else
    -- 添加互动
    redis.call('SADD', usersKey, userId)
    redis.call('SADD', userHistoryKey, countKey)
    local count = redis.call('INCR', countKey)
    return {count, activeAction}
end
