-- 热卖下单：查库存 + 扣减 + 订单消息入待确认队列（原子）
-- 消息是自包含 JSON，消费时凭它落库（list 即开单队列，消费才产生订单）
-- KEYS[1] = hot:stock:{goodsId}    可售库存计数
-- KEYS[2] = hot:pending:{goodsId}  待确认消息 list（成员 = 订单消息 JSON）
-- ARGV[1] = messageJson   ARGV[2] = quantity   ARGV[3] = buyerId   ARGV[4] = goodsId
-- 返回: 1=成功  0=库存不足  -1=库存未初始化(fail-close 拒单)  -2=该商品已有在途票(限一张在途)
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock < 0 then
    return -1
end
local qty = tonumber(ARGV[2])
if stock < qty then
    return 0
end

-- 同一买家同一商品只允许一张在途票：与 RPUSH 同脚本原子执行，双击/并发提交只有一方赢
for _, m in ipairs(redis.call('LRANGE', KEYS[2], 0, -1)) do
    local ok, msg = pcall(cjson.decode, m)
    if ok and msg and tostring(msg.buyerId) == ARGV[3] and tostring(msg.goodsId) == ARGV[4] then
        return -2
    end
end

redis.call('DECRBY', KEYS[1], qty)
redis.call('RPUSH', KEYS[2], ARGV[1])
return 1
