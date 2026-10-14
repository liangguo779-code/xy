package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.es.EsSyncHandler;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.EsOutboxService;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

/**
 * 商品库存占位服务：发起交易请求时扣减（0→3 占位），拒绝/过期/取消时恢复（3→0）。
 *
 * <p>扣减与恢复都在 Redis 分布式锁 {@code lock:goods:{goodsId}} 临界区内执行，
 * 与 {@code OrderServiceImpl.createOrder}、{@code OrderTimeoutService.restoreGoodsIfIdle}
 * 共用同一把商品锁，保证"占位/建单/回补"三类库存变更彼此互斥（防超卖）。
 *
 * <p>每次恢复都带持有者谓词（{@code WHERE reserve_ref = 自己}）：旧请求的释放
 * 不会把新占位翻回在售（P0-3 同类防护），重复释放天然幂等。
 *
 * <p>被发起交易的商品保留展示：列表/搜索/详情标记"已预订"（status=3 且 reserve_ref 非空属于可检索集），
 * 重复发起与直接下单均被拒绝；恢复后回到在售。ES 状态走事务性发件箱（{@code EsOutboxService}），
 * 与业务写同事务，ES 故障不影响库存一致性，恢复后由发件箱自动追平。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsReservationService {

    private final GoodsMapper goodsMapper;
    private final EsOutboxService esOutboxService;
    private final RedissonClient redisson;

    /**
     * 发起交易请求时扣减库存：仅当商品仍在售（0）才允许占位，成功后商品暂不显示。
     *
     * <p>同一会话重复发起（占位仍在自己手上）与其他买家抢占，分别给出不同提示。
     *
     * @param goodsId 商品 ID
     * @param sessionId 发起方聊天会话 ID，作为占位持有者
     */
    @Transactional(rollbackFor = Exception.class)
    public void reserveForRequest(Long goodsId, Long sessionId) {
        RLock lock = redisson.getLock("lock:goods:" + goodsId);
        lockGoods(lock, goodsId);
        try {
            // 先读一次给出精确错误（条件更新是最终仲裁，读值仅用于区分失败原因）
            Goods goods = goodsMapper.selectById(goodsId);
            if (goods == null) {
                throw new BusinessException("商品不存在");
            }
            if (goods.getStatus() == 3 && sessionId.equals(goods.getReserveRef())) {
                throw new BusinessException("已有一笔进行中的交易请求，请等待对方处理或过期后再发起");
            }
            if (goods.getStatus() != 0) {
                throw new BusinessException("商品已被其他买家预订或当前不可交易");
            }

            // 条件更新即原子扣减：仅当仍在售才占位，0 行说明已被并发抢占
            int reserved = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, goodsId)
                    .eq(Goods::getStatus, 0)
                    .set(Goods::getStatus, 3)
                    .set(Goods::getReserveRef, sessionId));
            if (reserved == 0) {
                throw new BusinessException("商品已被其他买家预订，暂不可发起交易");
            }

            log.info("发起交易占位成功: goodsId={}, sessionId={}", goodsId, sessionId);
            // 被发起交易的商品保留展示（列表/详情标记"已预订"）：同事务写发件箱，
            // 提交后自动同步 ES 最新状态，崩溃不丢；处理器会重读库取到 reserve_ref
            esOutboxService.recordIndex(EsSyncHandler.TYPE_GOODS, goodsId);
        } finally {
            unlockAfterCompletion(lock);
        }
    }

    /**
     * 释放占位（拒绝/过期/取消时调用）：仅释放自己持有的占位，恢复在售并重新上架显示。
     * 重复释放/非持有者释放是无副作用的空操作，可安全重试。
     *
     * @param goodsId 商品 ID
     * @param sessionId 释放方聊天会话 ID（占位持有者）
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseByRequest(Long goodsId, Long sessionId) {
        if (goodsId == null || sessionId == null) {
            return;
        }
        RLock lock = redisson.getLock("lock:goods:" + goodsId);
        lockGoods(lock, goodsId);
        try {
            int released = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, goodsId)
                    .eq(Goods::getStatus, 3)
                    .eq(Goods::getReserveRef, sessionId)
                    .set(Goods::getStatus, 0)
                    .set(Goods::getReserveRef, null)
                    .set(Goods::getOffReason, null));
            if (released == 0) {
                // 占位已被订单接管/已释放/已被他人持有：不做任何改动
                log.info("跳过库存恢复（非持有者或已释放）: goodsId={}, sessionId={}", goodsId, sessionId);
                return;
            }
            log.info("库存已恢复: goodsId={}, sessionId={}", goodsId, sessionId);
            // 商品重新上架显示：同事务写发件箱，提交后自动同步 ES（处理器重读库取最新状态）
            esOutboxService.recordIndex(EsSyncHandler.TYPE_GOODS, goodsId);
        } finally {
            unlockAfterCompletion(lock);
        }
    }

    // ============ private helpers ============

    /** 获取商品分布式锁（与下单/回补共用 lock:goods:{goodsId}），拿不到锁直接失败，绝不无锁改库存 */
    private void lockGoods(RLock lock, Long goodsId) {
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("系统异常，请稍后重试");
        }
        if (!locked) {
            throw new BusinessException("该商品库存正在变更中，请稍后重试");
        }
    }

    /** 事务提交/回滚后再释放锁，防止并发请求读到未提交的中间状态（与 createOrder 一致） */
    private void unlockAfterCompletion(RLock lock) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });
        } else if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
