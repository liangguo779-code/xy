package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.es.EsSyncHandler;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.common.service.EsOutboxService;
import com.campus.trade.dto.HotTicketVO;
import com.campus.trade.dto.PublishHotGoodsReq;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.HotSaleStockService;
import com.campus.trade.service.HotTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端热卖商品接口：仅 ADMIN 可发布/调整库存/下架（热卖商品不走用户发布与审核流程）。
 * 发布即写入 Redis 库存计数，热卖订单的并发扣减由 {@link HotSaleStockService} 的 Lua 脚本仲裁。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/hot-goods")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminHotGoodsController {

    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final HotSaleStockService stockService;
    private final HotTicketService ticketService;
    private final EsOutboxService esOutboxService;

    /** 发布热卖商品：写库 + 初始化 Redis 库存计数 + 写入 ES 同步发件箱，发布即上架 */
    @PostMapping
    @Transactional
    public R<Goods> publish(@RequestBody PublishHotGoodsReq req) {
        Long adminId = StpUtil.getLoginIdAsLong();

        Goods goods = new Goods();
        goods.setUserId(adminId);          // 发布管理员，作为订单卖家
        goods.setTitle(req.getTitle());
        goods.setDescription(req.getDescription());
        goods.setPrice(req.getPrice());
        goods.setOriginalPrice(req.getOriginalPrice());
        goods.setCategoryId(req.getCategoryId());
        goods.setImages(req.getImages());
        goods.setVideoUrl(req.getVideoUrl());
        goods.setCondition(req.getCondition() != null ? req.getCondition() : "全新");
        goods.setLocation(req.getLocation()); // 发货点：配送单取货地址
        goods.setType(0);                  // 出售
        goods.setGoodsType(1);             // 热卖
        goods.setStatus(0);                // 发布即上架，免审核
        goods.setStock(req.getStock());
        goods.setSales(0);
        goods.setPurchaseLimit(req.getPurchaseLimit());
        goods.setViewCount(0);
        goods.setWantCount(0);
        goods.setLikeCount(0);
        goodsMapper.insert(goods);

        // 初始化 Redis 库存计数（热卖扣减权威），同事务写 ES 同步意图（提交后自动同步）
        stockService.initStock(goods.getId(), req.getStock());
        esOutboxService.recordIndex(EsSyncHandler.TYPE_GOODS, goods.getId());

        log.info("热卖商品发布: goodsId={}, stock={}, adminId={}", goods.getId(), req.getStock(), adminId);
        return R.ok(goods);
    }

    /** 调整库存（补货用正数、盘亏用负数）：DB 与 Redis 同步，Redis 用 INCRBY 防冲掉在途扣减 */
    @PutMapping("/{id}/stock")
    public R<Goods> adjustStock(@PathVariable Long id, @RequestParam int delta) {
        Goods goods = requireHotGoods(id);

        // DB 条件更新防减成负数；补货直接加
        if (delta < 0) {
            int updated = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, id)
                    .ge(Goods::getStock, -delta)
                    .setSql("stock = stock + (" + delta + ")"));
            if (updated == 0) {
                throw new BusinessException("库存不足，无法减到负数");
            }
        } else if (delta > 0) {
            goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .eq(Goods::getId, id)
                    .setSql("stock = stock + " + delta));
        }
        stockService.adjustStock(id, delta);

        log.info("热卖库存调整: goodsId={}, delta={}", id, delta);
        return R.ok(goodsMapper.selectById(id));
    }

    /** 下架热卖商品：拒新单，在途订单（已扣库存）继续履约 */
    @PutMapping("/{id}/off")
    @Transactional
    public R<Void> forceOff(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Goods goods = requireHotGoods(id);
        int updated = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .eq(Goods::getStatus, 0)
                .set(Goods::getStatus, 1)
                .set(Goods::getOffReason, reason != null ? reason : "管理员下架"));
        if (updated == 0) {
            throw new BusinessException("商品已下架");
        }
        esOutboxService.recordDelete(EsSyncHandler.TYPE_GOODS, id);
        log.info("热卖商品下架: goodsId={}, reason={}", id, reason);
        return R.ok();
    }

    /** 热卖商品列表（按创建时间倒序），返回 Redis 可售库存（缺省回退 DB 值） */
    @GetMapping
    public R<Page<Goods>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getGoodsType, 1);
        if (status != null) {
            wrapper.eq(Goods::getStatus, status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword));
        }
        wrapper.orderByDesc(Goods::getCreateTime);
        Page<Goods> result = goodsMapper.selectPage(new Page<>(page, size), wrapper);
        // 展示口径：Redis 计数优先（含在途未确认扣减），key 缺失回退 DB 值
        result.getRecords().forEach(g -> {
            Long available = stockService.getAvailable(g.getId());
            if (available != null) {
                g.setStock(available.intValue());
            }
        });
        return R.ok(result);
    }

    /** 热卖订单监控：可按状态筛选（10=待用户确认，观察超时释放情况） */
    @GetMapping("/orders")
    public R<Page<Order>> orders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getGoodsType, 1);
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        } else {
            // 默认展示最近的热卖订单
        }
        wrapper.orderByDesc(Order::getCreateTime);
        return R.ok(orderMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /** 热卖票据监控：待确认/已确认/处理中三桶全量（订单尚未落库的在途单，前端与 DB 订单页合并展示） */
    @GetMapping("/tickets")
    public R<List<HotTicketVO>> tickets() {
        return R.ok(ticketService.adminTickets());
    }

    /** 库存概览：DB 可售/Redis 可售/在途票据数/队列积压，供对账观测 */
    @GetMapping("/{id}/stock-overview")
    public R<Map<String, Object>> stockOverview(@PathVariable Long id) {
        Goods goods = requireHotGoods(id);
        Long redisStock = stockService.getAvailable(id);
        Map<String, Object> overview = new HashMap<>();
        overview.put("goodsId", id);
        overview.put("dbStock", goods.getStock() != null ? goods.getStock() : 0);
        overview.put("redisStock", redisStock); // 可为 null（key 缺失，对账任务会补建）
        overview.put("sales", goods.getSales() != null ? goods.getSales() : 0);
        // 在途票据 = Redis 待确认桶长度（DB 无 status=10 行了，订单消费才落库）
        overview.put("pendingConfirmOrders", stockService.pendingLength(id));
        // 全局队列积压观测
        overview.put("confirmedQueueLen", stockService.confirmedQueueLength());
        overview.put("processingQueueLen", stockService.processingLength());
        return R.ok(overview);
    }

    private Goods requireHotGoods(Long id) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getGoodsType() == null || goods.getGoodsType() != 1) {
            throw new BusinessException("热卖商品不存在");
        }
        return goods;
    }
}
