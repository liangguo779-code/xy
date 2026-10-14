-- ============================================================
-- ES 同步事务性发件箱（Transactional Outbox）增量迁移
-- 已有库执行；全新安装直接用 init.sql 即可
--
-- 业务表与本表在同一个 DB 事务内写入，提交后由 EsOutboxService
-- 轮询/踢一脚把变更同步进 Elasticsearch。进程崩溃不丢同步意图。
-- ============================================================

USE campus;

CREATE TABLE IF NOT EXISTS `es_sync_outbox` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `biz_type`      VARCHAR(16) NOT NULL COMMENT '业务类型: goods-商品 post-帖子',
    `biz_id`        BIGINT      NOT NULL COMMENT '业务主键 ID',
    `op`            VARCHAR(8)  NOT NULL COMMENT '同步意图: index-重建文档 delete-移除文档',
    `status`        TINYINT     NOT NULL DEFAULT 0 COMMENT '0-待同步 1-已同步',
    `retry_count`   INT         NOT NULL DEFAULT 0 COMMENT '失败重试次数（指数退避）',
    `next_retry_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可重试时间',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 同一实体只保留"最新意图"一行：重复 record 走 ON DUPLICATE KEY UPDATE 覆盖
    UNIQUE KEY `uk_biz` (`biz_type`, `biz_id`),
    KEY `idx_poll` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ES 同步事务性发件箱';
