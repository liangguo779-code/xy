-- ============================================================
-- 热卖商品增量迁移（已有库执行；全新安装直接用 init.sql 即可）
-- ============================================================

USE campus;

ALTER TABLE `goods`
    ADD COLUMN `goods_type`     TINYINT DEFAULT 0 COMMENT '商品类型: 0-二手(一物一占位) 1-热卖(管理员发布,库存计数)',
    ADD COLUMN `stock`          INT DEFAULT NULL COMMENT '可售库存(仅热卖): 运行时以 Redis hot:stock:{id} 为扣减权威',
    ADD COLUMN `sales`          INT DEFAULT 0 COMMENT '已售数量(仅热卖): 二次确认成功时累加',
    ADD COLUMN `purchase_limit` INT DEFAULT NULL COMMENT '单人限购(仅热卖): NULL/0 不限购';

ALTER TABLE `order`
    ADD COLUMN `quantity`    INT DEFAULT 1 COMMENT '购买数量(热卖用), 二手固定1',
    ADD COLUMN `goods_type`  TINYINT DEFAULT 0 COMMENT '冗余商品类型: 0-二手 1-热卖',
    ADD COLUMN `expire_time` DATETIME COMMENT '二次确认截止(仅热卖待确认), 超时自动取消释放库存';
