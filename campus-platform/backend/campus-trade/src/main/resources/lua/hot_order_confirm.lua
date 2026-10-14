-- 热卖票据确认（购买承诺）：消息从待确认桶迁入已确认队列，等 worker 落库
-- 与超时释放互斥：两边都对同一成员串 LREM，恰好一方拿到 1（状态迁移的原子仲裁）
-- KEYS[1] = hot:pending:{goodsId}
-- KEYS[2] = hot:confirmed:queue
-- ARGV[1] = orderNo
-- 返回: 1=迁移成功  0=不存在（已被超时释放/已确认/已取消）
local members = redis.call('LRANGE', KEYS[1], 0, -1)
for _, m in ipairs(members) do
    local ok, msg = pcall(cjson.decode, m)
    if ok and msg and msg.orderNo == ARGV[1] then
        if redis.call('LREM', KEYS[1], 1, m) == 1 then
            redis.call('RPUSH', KEYS[2], m)   -- 原成员串原样迁移，状态=已确认
            return 1
        end
        return 0   -- LREM 输了竞争，恰好一次语义保持
    end
end
return 0
