package com.campus.trade.service;

import com.campus.common.es.EsSyncHandler;
import com.campus.trade.entity.Goods;
import com.campus.trade.mapper.GoodsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 商品 ES 同步处理器：把发件箱里的意图落到 {@code goods} 索引。
 *
 * <p>同步时重新读库，按可见性决定写入还是删除——与 {@code GoodsIndexService.reindexAll}
 * 的过滤口径一致（在售 0，或已预订 3 且 reserveRef 非空），避免"库里已下架、索引还在"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsEsSyncHandler implements EsSyncHandler {

    private final GoodsMapper goodsMapper;
    private final GoodsIndexService goodsIndexService;

    @Override
    public String bizType() {
        return TYPE_GOODS;
    }

    @Override
    public void syncIndex(Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            // 业务行已物理删除：索引也要清掉
            goodsIndexService.deleteGoods(goodsId);
            return;
        }
        if (isSearchable(goods)) {
            goodsIndexService.indexGoods(goods);
        } else {
            // 下架/售出/待审核等不可见状态：从索引移除，防止内容泄漏
            goodsIndexService.deleteGoods(goodsId);
        }
    }

    @Override
    public void syncDelete(Long goodsId) {
        goodsIndexService.deleteGoods(goodsId);
    }

    /**
     * 是否可被搜索：在售(0)，或已预订(3)但保留展示（reserveRef 非空）。
     * 口径与 GoodsSearchService 的可见性 filter、reindexAll 的入索引条件保持一致。
     */
    private boolean isSearchable(Goods goods) {
        Integer status = goods.getStatus();
        if (status == null) {
            return false;
        }
        return status == 0 || (status == 3 && goods.getReserveRef() != null);
    }
}
