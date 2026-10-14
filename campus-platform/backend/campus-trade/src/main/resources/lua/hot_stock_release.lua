-- 热卖票据释放（超时未确认 / 待确认期取消）：摘消息 + 回补库存，原子执行
-- LREM=0 说明已被确认摘走或已释放过，禁止二次回补（幂等）
-- KEYS[1] = hot:stock:{goodsId}
-- KEYS[2] = hot:pending:{goodsId}
-- ARGV[1] = orderNo
-- 返回: 1=已回补  0=消息不存在（已确认/已释放，无副作用）
local members = redis.call('LRANGE', KEYS[2], 0, -1)
for _, m in ipairs(members) do
    local ok, msg = pcall(cjson.decode, m)
    if ok and msg and msg.orderNo == ARGV[1] then
        if redis.call('LREM', KEYS[2], 1, m) == 1 then
            redis.call('INCRBY', KEYS[1], tonumber(msg.qty))
            return 1
        end
        return 0
    end
end
return 0
