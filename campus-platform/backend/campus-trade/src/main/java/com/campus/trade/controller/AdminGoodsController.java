package com.campus.trade.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.es.EsSyncHandler;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.common.service.EsOutboxService;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.service.GoodsIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端商品接口：商品审核、强制下架、全量重建搜索索引（需 ADMIN 角色） */
@RestController
@RequestMapping("/api/admin/goods")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminGoodsController {

    private final GoodsMapper goodsMapper;
    private final GoodsIndexService goodsIndexService;
    private final EsOutboxService esOutboxService;
    private final com.campus.trade.mapper.OrderMapper orderMapper;

    /** 分页查询商品，可按状态过滤、按标题/描述模糊搜索 */
    @GetMapping
    public R<Page<Goods>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Goods::getStatus, status);
        if (keyword != null) {
            wrapper.and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword));
        }
        wrapper.orderByDesc(Goods::getCreateTime);
        return R.ok(goodsMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 审核通过商品：状态改为已上架并写入搜索索引。
     *
     * <p>注意 Goods.status=3 是双义值：管理侧表示"待审核"，订单流程同时用作"已预订"锁定位（见 Goods.status 注释）。
     * 已被订单预订的商品必须跳过（有活跃订单即拒绝），否则会把预订中的商品放回在售，造成一货两卖。
     */
    @PutMapping("/{id}/approve")
    @Transactional
    public R<Void> approve(@PathVariable Long id) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) throw new BusinessException("商品不存在");
        if (goods.getStatus() != 3) {   // 3-待审核/已预订
            throw new BusinessException("只有待审核的商品才能审核通过");
        }

        // 检查是否有进行中的订单（status=3 可能是"已预订"而非"待审核"）
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<com.campus.trade.entity.Order>()
                        .eq(com.campus.trade.entity.Order::getGoodsId, id)
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.COMPLETED.getCode())
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有进行中的订单（已预订），无法审核通过");
        }
        // 请求占位中（发起交易未建单，reserve_ref=会话ID）同样是"已预订"，审核放行会把占位翻回在售
        if (goods.getReserveRef() != null) {
            throw new BusinessException("该商品已被交易请求占位（已预订），无法审核通过");
        }

        // 条件更新带上状态谓词（CAS）：仅当仍是待审核状态才放行，防止确认后被并发改状态
        int updated = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .eq(Goods::getStatus, 3)   // 3-待审核/已预订
                .isNull(Goods::getReserveRef) // 无请求占位才算纯"待审核"
                .set(Goods::getStatus, 0)   // 0-已上架
                .set(Goods::getOffReason, null));
        if (updated == 0) {
            throw new BusinessException("商品状态已变化，请刷新后重试");
        }
        goods.setStatus(0);
        esOutboxService.recordIndex(EsSyncHandler.TYPE_GOODS, id);
        return R.ok();
    }

    /** 拒绝商品（下架并记录原因），已售出的不可拒绝；已有活跃订单（已预订）的商品跳过，避免误操作 */
    @PutMapping("/{id}/reject")
    @Transactional
    public R<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) throw new BusinessException("商品不存在");
        if (goods.getStatus() == 2) {   // 2-已售出
            throw new BusinessException("已售出的商品不能拒绝");
        }

        // 检查是否有进行中的订单（status=3 可能是订单流程的"已预订"锁定位而非"待审核"）
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<com.campus.trade.entity.Order>()
                        .eq(com.campus.trade.entity.Order::getGoodsId, id)
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.COMPLETED.getCode())
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有 " + activeOrders + " 个进行中的订单，请先处理订单后再操作");
        }

        // 条件更新带状态谓词（CAS）：仅当状态未被并发修改才下架，防止把已预订/已售出商品误下架
        int updated = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .eq(Goods::getStatus, goods.getStatus())
                .set(Goods::getStatus, 1)   // 1-已下架
                .set(Goods::getOffReason, "管理员拒绝：" + (reason != null ? reason : "违规内容")));
        if (updated == 0) {
            throw new BusinessException("商品状态已变化，请刷新后重试");
        }
        esOutboxService.recordDelete(EsSyncHandler.TYPE_GOODS, id);
        return R.ok();
    }

    /** 强制下架商品（在售/已预订状态可操作），并从搜索索引移除；已有活跃订单（已预订）的商品跳过 */
    @PutMapping("/{id}/force-off")
    @Transactional
    public R<Void> forceOff(@PathVariable Long id, @RequestParam(required = false) String reason) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) throw new BusinessException("商品不存在");
        if (goods.getStatus() != 0 && goods.getStatus() != 3) {   // 0-已上架 3-待审核/已预订
            throw new BusinessException("只有在售或已预订的商品才能强制下架");
        }

        // 检查是否有进行中的订单（status=3 可能是订单流程的"已预订"锁定位而非"待审核"）
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<com.campus.trade.entity.Order>()
                        .eq(com.campus.trade.entity.Order::getGoodsId, id)
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.COMPLETED.getCode())
                        .ne(com.campus.trade.entity.Order::getStatus, com.campus.trade.enums.OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有 " + activeOrders + " 个进行中的订单，请先处理订单后再操作");
        }

        // 条件更新带状态谓词（CAS）：仅当状态未被并发修改才下架，防止与并发下单预订交错误操作
        int updated = goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .eq(Goods::getStatus, goods.getStatus())
                .set(Goods::getStatus, 1)
                .set(Goods::getOffReason, "管理员强制下架：" + (reason != null ? reason : "")));
        if (updated == 0) {
            throw new BusinessException("商品状态已变化，请刷新后重试");
        }
        esOutboxService.recordDelete(EsSyncHandler.TYPE_GOODS, id);
        return R.ok();
    }

    /** 商品总览统计：总数/在售/已预订/已售出 */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        long total = goodsMapper.selectCount(null);
        long onSale = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0));
        long reserved = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 3));
        long sold = goodsMapper.selectCount(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 2));
        return R.ok(Map.of("total", total, "onSale", onSale, "reserved", reserved, "sold", sold));
    }

    /** 全量重建商品搜索索引（ES 数据异常时手动触发） */
    @PutMapping("/reindex")
    public R<Void> reindex() {
        goodsIndexService.reindexAll();
        return R.ok();
    }
}
