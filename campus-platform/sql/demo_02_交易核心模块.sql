-- ============================================================
-- 演示 SQL ②：交易核心模块
-- 核心表：category → goods → order → delivery_order → review → dispute
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus;

-- 1. 商品分类表
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
    `icon`        VARCHAR(200) COMMENT '图标URL',
    `sort_order`  INT DEFAULT 0 COMMENT '排序',
    `parent_id`   BIGINT DEFAULT 0 COMMENT '父分类ID, 0为顶级',
    `status`      TINYINT DEFAULT 1 COMMENT '0-禁用 1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 2. 商品表（关联 user + category）
CREATE TABLE IF NOT EXISTS `goods` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`        BIGINT NOT NULL COMMENT '卖家ID',
    `title`          VARCHAR(200) NOT NULL COMMENT '标题',
    `description`    TEXT COMMENT '描述',
    `price`          DECIMAL(10,2) NOT NULL COMMENT '售价',
    `original_price` DECIMAL(10,2) COMMENT '原价',
    `category_id`    BIGINT COMMENT '分类ID',
    `category`       VARCHAR(50) COMMENT '分类名称(冗余)',
    `images`         JSON COMMENT '图片URL数组',
    `condition`      VARCHAR(20) DEFAULT '良好' COMMENT '成色: 全新/几乎全新/良好/一般',
    `type`           TINYINT DEFAULT 0 COMMENT '0-出售 1-求购',
    `status`         TINYINT DEFAULT 0 COMMENT '0-上架 1-下架 2-售出 3-待审核',
    `view_count`     INT DEFAULT 0 COMMENT '浏览量',
    `like_count`     INT DEFAULT 0 COMMENT '收藏数',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_category` (`category_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 3. 订单表（关联 goods + user）
CREATE TABLE IF NOT EXISTS `order` (
    `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_no`         VARCHAR(32) UNIQUE NOT NULL COMMENT '订单号',
    `goods_id`         BIGINT NOT NULL COMMENT '商品ID',
    `buyer_id`         BIGINT NOT NULL COMMENT '买家ID',
    `seller_id`        BIGINT NOT NULL COMMENT '卖家ID',
    `deal_type`        TINYINT NOT NULL COMMENT '0-自提 1-配送',
    `goods_amount`     DECIMAL(10,2) NOT NULL COMMENT '成交价',
    `service_fee`      DECIMAL(10,2) DEFAULT 0 COMMENT '配送服务费',
    `verify_code`      VARCHAR(8) COMMENT '自提核销码',
    `status`           TINYINT DEFAULT 0 COMMENT '状态(多段流转)',
    `pickup_location`  VARCHAR(200) COMMENT '自提地点',
    `complete_time`    DATETIME COMMENT '完成时间',
    `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_buyer` (`buyer_id`),
    INDEX `idx_seller` (`seller_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单表';

-- 4. 配送工单表（关联 order + user）
CREATE TABLE IF NOT EXISTS `delivery_order` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id`       BIGINT NOT NULL COMMENT '关联订单ID',
    `runner_id`      BIGINT COMMENT '交付员ID',
    `buyer_addr`     VARCHAR(200) NOT NULL COMMENT '收货地址',
    `pickup_photo`   VARCHAR(500) COMMENT '取货存证',
    `deliver_photo`  VARCHAR(500) COMMENT '送达存证',
    `status`         TINYINT DEFAULT 0 COMMENT '0-待接单 1-待取货 2-配送中 3-已送达',
    `delivery_fee`   DECIMAL(10,2) DEFAULT 0 COMMENT '配送费',
    `accept_time`    DATETIME COMMENT '接单时间',
    `deliver_time`   DATETIME COMMENT '送达时间',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_order` (`order_id`),
    INDEX `idx_runner` (`runner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送工单表';

-- 5. 评价表（关联 order + user）
CREATE TABLE IF NOT EXISTS `review` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id`      BIGINT NOT NULL COMMENT '订单ID',
    `reviewer_id`   BIGINT NOT NULL COMMENT '评价者ID',
    `target_id`     BIGINT NOT NULL COMMENT '被评价者ID',
    `rating`        TINYINT NOT NULL COMMENT '评分 1-5',
    `content`       VARCHAR(500) COMMENT '评价内容',
    `tags`          JSON COMMENT '评价标签',
    `status`        TINYINT DEFAULT 1 COMMENT '1-正常 0-屏蔽',
    `appeal_status` TINYINT DEFAULT 0 COMMENT '0-无申诉 1-申诉中 2-通过 3-驳回',
    `reply`         VARCHAR(500) COMMENT '被评价者回复',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_reviewer` (`order_id`, `reviewer_id`),
    INDEX `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- 6. 纠纷仲裁表（关联 order + user）
CREATE TABLE IF NOT EXISTS `dispute` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id`        BIGINT NOT NULL,
    `reporter_id`     BIGINT NOT NULL COMMENT '发起人',
    `reason`          VARCHAR(500) NOT NULL COMMENT '纠纷原因',
    `evidence_images` JSON COMMENT '证据图片',
    `status`          TINYINT DEFAULT 0 COMMENT '0-待处理 1-处理中 2-已解决 3-已驳回',
    `result`          VARCHAR(500) COMMENT '处理结果',
    `handler_id`      BIGINT COMMENT '处理人',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_order` (`order_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='纠纷仲裁表';

-- ========== 外键约束 ==========
ALTER TABLE `goods`          ADD CONSTRAINT `fk_goods_user`     FOREIGN KEY (`user_id`)     REFERENCES `user`(`id`)      ON DELETE CASCADE;
ALTER TABLE `goods`          ADD CONSTRAINT `fk_goods_category` FOREIGN KEY (`category_id`) REFERENCES `category`(`id`)  ON DELETE SET NULL;
ALTER TABLE `order`          ADD CONSTRAINT `fk_order_goods`    FOREIGN KEY (`goods_id`)    REFERENCES `goods`(`id`)     ON DELETE CASCADE;
ALTER TABLE `order`          ADD CONSTRAINT `fk_order_buyer`    FOREIGN KEY (`buyer_id`)    REFERENCES `user`(`id`)      ON DELETE CASCADE;
ALTER TABLE `order`          ADD CONSTRAINT `fk_order_seller`   FOREIGN KEY (`seller_id`)   REFERENCES `user`(`id`)      ON DELETE CASCADE;
ALTER TABLE `delivery_order` ADD CONSTRAINT `fk_delivery_order`  FOREIGN KEY (`order_id`)   REFERENCES `order`(`id`)     ON DELETE CASCADE;
ALTER TABLE `review`         ADD CONSTRAINT `fk_review_order`    FOREIGN KEY (`order_id`)   REFERENCES `order`(`id`)     ON DELETE CASCADE;
ALTER TABLE `dispute`        ADD CONSTRAINT `fk_dispute_order`   FOREIGN KEY (`order_id`)   REFERENCES `order`(`id`)     ON DELETE CASCADE;

-- ========== 示例数据 ==========
INSERT INTO `category` (`name`, `sort_order`) VALUES
('数码', 1), ('教材', 2), ('生活', 3), ('其他', 99);

-- 假设已有用户: id=2(张三), id=3(李四)
INSERT INTO `goods` (`user_id`, `title`, `description`, `price`, `category_id`, `category`, `condition`, `status`) VALUES
(2, '二手iPhone 14', '99新，使用3个月', 3500.00, 1, '数码', '几乎全新', 0),
(2, '高等数学教材', '第六版，无笔记', 25.00, 2, '教材', '良好', 0),
(3, '台灯', 'LED护眼台灯', 40.00, 3, '生活', '良好', 0);

INSERT INTO `order` (`order_no`, `goods_id`, `buyer_id`, `seller_id`, `deal_type`, `goods_amount`, `status`) VALUES
('ORD202607170001', 1, 3, 2, 0, 3500.00, 3),
('ORD202607170002', 2, 3, 2, 0, 25.00, 1);
