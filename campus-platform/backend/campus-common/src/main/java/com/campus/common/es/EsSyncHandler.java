package com.campus.common.es;

/**
 * ES 同步处理器 SPI：按业务类型把发件箱里的同步意图落到具体的 Elasticsearch 索引。
 *
 * <p>由各业务模块实现（campus-trade → goods 索引，campus-forum → posts 索引），
 * {@code EsOutboxService} 按 {@link #bizType()} 分发，common 层不依赖任何业务模块。
 *
 * <p>实现约定：同步时<b>重新读库</b>取最新状态，不要依赖调用方传入的实体快照——
 * 发件箱只记录"要同步谁"，状态以数据库为准，这样重放天然幂等。
 */
public interface EsSyncHandler {

    /** 商品（campus-trade，索引名 goods） */
    String TYPE_GOODS = "goods";
    /** 帖子（campus-forum，索引名 posts） */
    String TYPE_POST = "post";

    /** 同步意图：重建/覆盖文档 */
    String OP_INDEX = "index";
    /** 同步意图：移除文档 */
    String OP_DELETE = "delete";

    /** 本处理器负责的业务类型，对应发件箱 biz_type 列 */
    String bizType();

    /**
     * 按库内最新状态重建文档。
     *
     * <p>实现需自行判断可见性：不可见（下架/售出/已删/待审核）时应转为删除文档，
     * 避免"库里已隐藏、索引还在"的内容泄漏。
     *
     * @param bizId 业务主键 ID
     */
    void syncIndex(Long bizId);

    /**
     * 从索引移除文档。重复调用应无副作用（幂等）。
     *
     * @param bizId 业务主键 ID
     */
    void syncDelete(Long bizId);
}
