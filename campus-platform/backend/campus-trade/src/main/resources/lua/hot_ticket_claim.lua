-- 消费者认领已确认消息：LPOP 已确认队列 + RPUSH 进处理中转区，原子执行
-- 处理中转区 = 「已认领、尚未落库/尚未摘除」的可靠消费缓冲，崩溃后由对账兜底
-- KEYS[1] = hot:confirmed:queue
-- KEYS[2] = hot:order:processing
-- 返回: 消息 JSON / nil（队列已空）
local m = redis.call('LPOP', KEYS[1])
if not m then
    return nil
end
redis.call('RPUSH', KEYS[2], m)
return m
