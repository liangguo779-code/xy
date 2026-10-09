package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.common.service.NotificationService;
import com.campus.trade.entity.BrowseHistory;
import com.campus.trade.entity.Category;
import com.campus.trade.entity.Favorite;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.BrowseHistoryMapper;
import com.campus.trade.mapper.CategoryMapper;
import com.campus.trade.mapper.FavoriteMapper;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.GoodsIndexService;
import com.campus.trade.service.GoodsSearchService;
import com.campus.trade.service.GoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** 商品服务实现，负责商品检索/推荐/详情与发布、编辑、上下架等生命周期管理，并同步 ES 索引 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements GoodsService {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final GoodsMapper goodsMapper;
    private final FavoriteMapper favoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final CategoryMapper categoryMapper;
    private final OrderMapper orderMapper;
    private final NotificationService notificationService;
    private final GoodsSearchService goodsSearchService;
    private final GoodsIndexService goodsIndexService;

    /** 商品列表：有关键字时优先 ES 检索，ES 失败或无结果自动降级 MySQL 模糊查询 */
    @Override
    public Page<Goods> listGoods(String keyword, Long categoryId, Long userId, Integer type,
                                 BigDecimal minPrice, BigDecimal maxPrice,
                                 String condition, String sortBy,
                                 int page, int size) {
        // 有 keyword 且不是"我的商品"场景时，走 ES 搜索
        if (StringUtils.hasText(keyword) && userId == null) {
            try {
                Page<Goods> esResult = goodsSearchService.searchGoods(keyword, categoryId, type,
                        minPrice, maxPrice, condition, sortBy, page, size);
                // ES 返回结果或无关键词过滤时直接返回
                if (esResult.getTotal() > 0 || !StringUtils.hasText(keyword)) {
                    log.info("ES 搜索成功: keyword={}, results={}", keyword, esResult.getTotal());
                    return esResult;
                }
                log.info("ES 搜索无结果，降级到 MySQL: keyword={}", keyword);
            } catch (Exception e) {
                log.warn("ES 搜索失败，降级到 MySQL: {}", e.getMessage());
            }
        }

        Page<Goods> mysqlResult = listGoodsFromMySQL(keyword, categoryId, userId, type,
                minPrice, maxPrice, condition, sortBy, page, size);
        log.info("MySQL 搜索: keyword={}, results={}", keyword, mysqlResult.getTotal());
        return mysqlResult;
    }

    /** MySQL 降级查询：在售 + 已预订商品（已预订展示角标），支持多条件过滤与价格/热度/最新排序 */
    private Page<Goods> listGoodsFromMySQL(String keyword, Long categoryId, Long userId, Integer type,
                                            BigDecimal minPrice, BigDecimal maxPrice,
                                            String condition, String sortBy,
                                            int page, int size) {
        // 可见范围：在售(0) 或 已预订(3 且有占位持有者)；待审核/下架/售出不出现
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .and(w -> w.eq(Goods::getStatus, 0)
                        .or(x -> x.eq(Goods::getStatus, 3).isNotNull(Goods::getReserveRef)));

        if (userId != null) {
            wrapper.eq(Goods::getUserId, userId);
        }

        if (type != null) {
            wrapper.eq(Goods::getType, type);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Goods::getTitle, keyword)
                    .or().like(Goods::getDescription, keyword));
        }

        if (categoryId != null) {
            wrapper.eq(Goods::getCategoryId, categoryId);
        }

        if (minPrice != null) {
            wrapper.ge(Goods::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Goods::getPrice, maxPrice);
        }

        if (StringUtils.hasText(condition)) {
            wrapper.eq(Goods::getCondition, condition);
        }

        // 排序
        if ("price_asc".equals(sortBy)) {
            wrapper.orderByAsc(Goods::getPrice);
        } else if ("price_desc".equals(sortBy)) {
            wrapper.orderByDesc(Goods::getPrice);
        } else if ("hottest".equals(sortBy)) {
            wrapper.orderByDesc(Goods::getViewCount);
        } else {
            // 默认按最新（擦亮优先）
            wrapper.orderByDesc(Goods::getRefreshTime).orderByDesc(Goods::getCreateTime);
        }

        return page(new Page<>(page, size), wrapper);
    }

    /** 热门推荐（无个性化），委托给带 userId 的重载 */
    @Override
    public Page<Goods> recommendGoods(int page, int size) {
        return recommendGoods(null, page, size);
    }

    /** 推荐商品：有收藏记录时按收藏分类过滤，否则按浏览/想要热度排序 */
    @Override
    public Page<Goods> recommendGoods(Long userId, int page, int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .eq(Goods::getType, 0);

        // 个性化推荐：基于用户收藏的商品分类
        if (userId != null) {
            List<Long> favGoodsIds = favoriteMapper.selectList(
                    new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId))
                    .stream().map(Favorite::getGoodsId).collect(Collectors.toList());

            if (!favGoodsIds.isEmpty()) {
                List<Goods> favGoods = goodsMapper.selectBatchIds(favGoodsIds);
                List<Long> categoryIds = favGoods.stream()
                        .map(Goods::getCategoryId).filter(java.util.Objects::nonNull)
                        .distinct().collect(Collectors.toList());

                if (!categoryIds.isEmpty()) {
                    wrapper.in(Goods::getCategoryId, categoryIds);
                }
            }
        }

        wrapper.orderByDesc(Goods::getViewCount).orderByDesc(Goods::getWantCount);
        return page(new Page<>(page, size), wrapper);
    }

    /** 商品详情（匿名访问，不记浏览历史） */
    @Override
    public Goods getGoodsDetail(Long id) {
        return getGoodsDetail(id, null);
    }

    /** 商品详情：浏览数+1，并记录浏览历史（1 小时内同商品去重） */
    @Override
    public Goods getGoodsDetail(Long id, Long userId) {
        Goods goods = getById(id);
        if (goods == null) {
            throw new BusinessException("商品不存在");
        }
        update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, id)
                .setSql("view_count = view_count + 1"));
        // 手动更新返回对象的 viewCount，避免前端显示旧值
        goods.setViewCount(goods.getViewCount() != null ? goods.getViewCount() + 1 : 1);
        // 记录浏览历史（1小时内同商品不重复记录）
        if (userId != null) {
            Long existCount = browseHistoryMapper.selectCount(
                    new LambdaQueryWrapper<BrowseHistory>()
                            .eq(BrowseHistory::getUserId, userId)
                            .eq(BrowseHistory::getGoodsId, id)
                            .ge(BrowseHistory::getCreateTime, java.time.LocalDateTime.now().minusHours(1)));
            if (existCount == 0) {
                BrowseHistory history = new BrowseHistory();
                history.setUserId(userId);
                history.setGoodsId(id);
                browseHistoryMapper.insert(history);
            }
        }
        return goods;
    }

    /** 发布商品：未选分类自动归入"其他"，服务端强制上架并同步 ES 索引 */
    @Override
    public Goods createGoods(Long userId, Goods goods) {
        // 未选分类时自动归入"其他"
        if (goods.getCategoryId() == null) {
            Category other = categoryMapper.selectOne(
                    new LambdaQueryWrapper<Category>().eq(Category::getName, "其他"));
            if (other != null) {
                goods.setCategoryId(other.getId());
            }
        }
        // 校验分类是否存在且启用
        if (goods.getCategoryId() != null) {
            Category category = categoryMapper.selectById(goods.getCategoryId());
            if (category == null || category.getStatus() != 1) {
                throw new BusinessException("商品分类不存在或已禁用");
            }
        }
        // 强制设置字段，防止前端篡改
        goods.setUserId(userId);
        goods.setStatus(0);  // 始终为已上架
        if (goods.getType() == null) {
            goods.setType(0);
        }
        goods.setViewCount(0);
        goods.setWantCount(0);
        goods.setLikeCount(0);
        save(goods);
        goodsIndexService.indexGoods(goods);
        return goods;
    }

    /** 编辑商品：仅本人在售商品可改，白名单字段更新，记录价格历史并在降价时通知收藏者 */
    @Override
    @Transactional
    public void updateGoods(Long userId, Long goodsId, Goods goods) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        // 只有在售商品可编辑
        if (existing.getStatus() != 0) {
            throw new BusinessException("只有在售商品可以编辑");
        }
        // 保存旧价格，用于比较
        BigDecimal oldPrice = existing.getPrice();

        // 只允许修改特定字段，防止篡改归属和状态
        existing.setTitle(goods.getTitle());
        existing.setDescription(goods.getDescription());
        existing.setPrice(goods.getPrice());
        existing.setOriginalPrice(goods.getOriginalPrice());
        existing.setCategory(goods.getCategory());
        existing.setCategoryId(goods.getCategoryId());
        existing.setCondition(goods.getCondition());
        existing.setImages(goods.getImages());
        existing.setVideoUrl(goods.getVideoUrl());

        // 记录价格变化历史
        if (goods.getPrice() != null && oldPrice != null
                && goods.getPrice().compareTo(oldPrice) != 0) {
            try {
                java.util.List<java.util.Map<String, Object>> historyList;
                String history = existing.getPriceHistory();
                if (history == null || history.isEmpty()) {
                    historyList = new java.util.ArrayList<>();
                } else {
                    historyList = OBJECT_MAPPER.readValue(history,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                }
                java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("price", oldPrice);
                entry.put("date", java.time.LocalDate.now().toString());
                historyList.add(entry);
                existing.setPriceHistory(OBJECT_MAPPER.writeValueAsString(historyList));
            } catch (Exception e) {
                // 价格历史记录失败不影响主流程
            }
        }

        // 降价提醒：如果价格降低，通知收藏该商品的用户
        if (goods.getPrice() != null && oldPrice != null
                && goods.getPrice().compareTo(oldPrice) < 0) {
            List<Favorite> favorites = favoriteMapper.selectList(
                    new LambdaQueryWrapper<Favorite>().eq(Favorite::getGoodsId, goodsId));
            for (Favorite fav : favorites) {
                if (!fav.getUserId().equals(userId)) {
                    notificationService.send(fav.getUserId(), "price_drop",
                            "商品降价提醒",
                            "你收藏的「" + existing.getTitle() + "」降价了！¥" + oldPrice + " → ¥" + goods.getPrice(),
                            "{\"goodsId\":" + goodsId + "}");
                }
            }
        }

        // 防止整行覆盖把并发变更的状态冲掉：status 不参与本次更新（置空后 MP 跳过该列），
        // 否则编辑保存可能把已预订（3）的旧读值 0 写回去，造成一货两卖（P3-8 同类防护）
        Integer statusSnapshot = existing.getStatus(); // 前面已校验为在售(0)
        existing.setStatus(null);
        updateById(existing);
        existing.setStatus(statusSnapshot); // 回填内存对象，供 ES 索引使用
        goodsIndexService.indexGoods(existing);
    }

    /** 下架商品（无原因） */
    @Override
    public void deleteGoods(Long userId, Long goodsId) {
        deleteGoods(userId, goodsId, null);
    }

    /** 下架商品并记录原因，有进行中订单时禁止下架，同时移除 ES 索引 */
    public void deleteGoods(Long userId, Long goodsId, String reason) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if (existing.getStatus() != 0) {
            throw new BusinessException("商品不在售状态，无法下架");
        }
        // 检查是否有进行中的订单
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getGoodsId, goodsId)
                        .ne(Order::getStatus, OrderStatus.COMPLETED.getCode())
                        .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有进行中的订单，请先完成或取消订单");
        }
        // 条件更新防 check-then-act 竞态：仅当商品仍在售才允许下架，
        // 防止与并发下单预订（0→3）交错把已预订商品误下架（P3-8）
        boolean updated = update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .eq(Goods::getStatus, 0)
                .set(Goods::getStatus, 1)
                .set(Goods::getOffReason, reason));
        if (!updated) {
            throw new BusinessException("商品状态已变化，请刷新后重试");
        }
        goodsIndexService.deleteGoods(goodsId);
    }

    /** 标记已售出，有进行中订单时禁止操作，同时移除 ES 索引 */
    @Override
    public void markAsSold(Long userId, Long goodsId) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if (existing.getStatus() != 0) {
            throw new BusinessException("商品不在售状态");
        }
        // 检查是否有进行中的订单
        Long activeOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getGoodsId, goodsId)
                        .ne(Order::getStatus, OrderStatus.COMPLETED.getCode())
                        .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));
        if (activeOrders > 0) {
            throw new BusinessException("该商品有进行中的订单，请先完成或取消订单");
        }
        // 条件更新防 check-then-act 竞态：仅当商品仍在售才允许标记售出，
        // 防止与并发下单预订（0→3）交错把已预订商品误标为已售出（P3-8）
        boolean updated = update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .eq(Goods::getStatus, 0)
                .set(Goods::getStatus, 2));
        if (!updated) {
            throw new BusinessException("商品状态已变化，请刷新后重试");
        }
        goodsIndexService.deleteGoods(goodsId);
    }

    /** 擦亮商品（刷新 refreshTime 提升排序），每天限 1 次 */
    @Override
    public void refreshGoods(Long userId, Long goodsId) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if (existing.getStatus() != 0) {
            throw new BusinessException("商品不在售状态");
        }
        // 检查今天是否已擦亮
        if (existing.getRefreshTime() != null) {
            java.time.LocalDate lastRefresh = existing.getRefreshTime().toLocalDate();
            if (lastRefresh.equals(java.time.LocalDate.now())) {
                throw new BusinessException("今天已经擦亮过了，明天再来吧");
            }
        }
        update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .set(Goods::getRefreshTime, java.time.LocalDateTime.now()));
        existing.setRefreshTime(java.time.LocalDateTime.now());
        goodsIndexService.indexGoods(existing);
    }

    /** 重新上架已下架商品，并刷新排序时间与 ES 索引 */
    @Override
    public void reshelfGoods(Long userId, Long goodsId) {
        Goods existing = getById(goodsId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if (existing.getStatus() != 1) {
            throw new BusinessException("只有已下架的商品才能重新上架");
        }
        update(new LambdaUpdateWrapper<Goods>()
                .eq(Goods::getId, goodsId)
                .set(Goods::getStatus, 0)
                .set(Goods::getOffReason, null)
                .set(Goods::getRefreshTime, java.time.LocalDateTime.now()));
        existing.setStatus(0);
        existing.setRefreshTime(java.time.LocalDateTime.now());
        goodsIndexService.indexGoods(existing);
    }

    /** 我的商品列表，可按状态过滤，按发布时间倒序 */
    @Override
    public Page<Goods> listMyGoods(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getUserId, userId);
        if (status != null) {
            wrapper.eq(Goods::getStatus, status);
        }
        wrapper.orderByDesc(Goods::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /** 浏览历史分页列表，按浏览时间倒序，已删除商品自动跳过 */
    @Override
    public Page<Goods> getBrowseHistory(Long userId, int page, int size) {
        // 查询浏览历史，去重取最新
        Page<BrowseHistory> historyPage = browseHistoryMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BrowseHistory>()
                        .eq(BrowseHistory::getUserId, userId)
                        .orderByDesc(BrowseHistory::getCreateTime));
        Page<Goods> result = new Page<>(page, size, historyPage.getTotal());
        List<Goods> goods = historyPage.getRecords().stream()
                .map(h -> getById(h.getGoodsId()))
                .filter(g -> g != null)
                .collect(Collectors.toList());
        result.setRecords(goods);
        return result;
    }
}
