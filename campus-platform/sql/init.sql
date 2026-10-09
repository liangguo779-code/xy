CREATE DATABASE IF NOT EXISTS campus DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE campus;

SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;

-- ============================================================
-- 用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username`    VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    `nickname`    VARCHAR(50) COMMENT '昵称',
    `avatar`      VARCHAR(255) COMMENT '头像URL',
    `phone`       VARCHAR(20) COMMENT '手机号',
    `dormitory`   VARCHAR(100) COMMENT '宿舍地址(配送用)',
    `role`        TINYINT DEFAULT 0 COMMENT '角色: 0-普通用户 1-管理员 2-交付员',
    `status`      TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 商品表
-- 状态流转: 已上架(0) → 已下架(1) / 已售出(2)
-- ============================================================
CREATE TABLE IF NOT EXISTS `goods` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`        BIGINT NOT NULL COMMENT '卖家ID',
    `title`          VARCHAR(200) NOT NULL COMMENT '标题',
    `description`    TEXT COMMENT '描述',
    `price`          DECIMAL(10,2) NOT NULL COMMENT '期望售价',
    `original_price` DECIMAL(10,2) COMMENT '原价',
    `category_id`    BIGINT COMMENT '分类ID',
    `category`       VARCHAR(50) COMMENT '分类名称(冗余)',
    `images`         JSON COMMENT '图片URL数组',
    `video_url`      VARCHAR(500) COMMENT '视频URL',
    `condition`      VARCHAR(20) DEFAULT '良好' COMMENT '成色: 全新/几乎全新/良好/一般',
    `location`       VARCHAR(100) COMMENT '位置(用于距离筛选)',
    `type`           TINYINT DEFAULT 0 COMMENT '类型: 0-出售 1-求购',
    `status`         TINYINT DEFAULT 0 COMMENT '状态: 0-已上架 1-已下架 2-已售出 3-待审核/已预订',
    `reserve_ref`    BIGINT DEFAULT NULL COMMENT '占位持有者: 交易请求会话ID或订单ID, NULL=无占位',
    `view_count`     INT DEFAULT 0 COMMENT '浏览量',
    `want_count`     INT DEFAULT 0 COMMENT '"我想要"次数',
    `like_count`     INT DEFAULT 0 COMMENT '收藏数',
    `price_history`  JSON DEFAULT NULL COMMENT '价格变化历史',
    `refresh_time`   DATETIME DEFAULT NULL COMMENT '最近擦亮时间',
    `off_reason`     VARCHAR(200) COMMENT '下架原因',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_category` (`category_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ============================================================
-- 商品分类表
-- ============================================================
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name`        VARCHAR(50) NOT NULL COMMENT '分类名称',
    `icon`        VARCHAR(200) COMMENT '图标URL',
    `sort_order`  INT DEFAULT 0 COMMENT '排序',
    `parent_id`   BIGINT DEFAULT 0 COMMENT '父分类ID, 0为顶级',
    `status`      TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ============================================================
-- 聊天会话表
-- 每个买家对每个商品只能有一个会话
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `goods_id`    BIGINT NOT NULL COMMENT '关联商品ID',
    `buyer_id`    BIGINT NOT NULL COMMENT '买家ID',
    `seller_id`   BIGINT NOT NULL COMMENT '卖家ID',
    `last_msg`    VARCHAR(500) COMMENT '最后一条消息预览',
    `last_time`   DATETIME COMMENT '最后消息时间',
    `status`      TINYINT DEFAULT 1 COMMENT '状态: 0-关闭 1-活跃',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_goods_buyer` (`goods_id`, `buyer_id`),
    INDEX `idx_buyer` (`buyer_id`),
    INDEX `idx_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- ============================================================
-- 聊天消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id`  BIGINT NOT NULL COMMENT '会话ID',
    `sender_id`   BIGINT NOT NULL COMMENT '发送者ID',
    `msg_type`    TINYINT DEFAULT 0 COMMENT '类型: 0-文本 1-图片 2-系统通知 3-快捷操作',
    `content`     VARCHAR(2000) NOT NULL COMMENT '消息内容',
    `extra`       TEXT COMMENT '扩展数据(图片URL/操作参数等)',
    `is_read`     TINYINT DEFAULT 0 COMMENT '是否已读: 0-未读 1-已读',
    `recall_time` DATETIME DEFAULT NULL COMMENT '撤回时间（null表示未撤回）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session` (`session_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- ============================================================
-- 交易订单表
-- deal_type: 0-线下自提 1-平台配送
--
-- 自提状态流转:
--   待确认(0) → 已确认/待核销(1) → 已完成(3) / 已取消(4)
--
-- 配送状态流转:
--   待支付服务费(5) → 服务费已付/待派单(6) → 已派单/待取货(7)
--   → 已取货/配送中(8) → 已送达/待确认收货(9) → 已完成(3) / 已取消(4)
-- ============================================================
CREATE TABLE IF NOT EXISTS `order` (
    `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_no`         VARCHAR(32) UNIQUE NOT NULL COMMENT '订单号',
    `goods_id`         BIGINT NOT NULL COMMENT '商品ID',
    `buyer_id`         BIGINT NOT NULL COMMENT '买家ID',
    `seller_id`        BIGINT NOT NULL COMMENT '卖家ID',
    `deal_type`        TINYINT NOT NULL COMMENT '交易方式: 0-自提 1-配送',
    `goods_amount`     DECIMAL(10,2) NOT NULL COMMENT '商品成交价(线下结算,系统记录)',
    `service_fee`      DECIMAL(10,2) DEFAULT 0 COMMENT '平台配送服务费',
    `delivery_fee_payer` VARCHAR(10) DEFAULT 'buyer' COMMENT '跑腿费付款方: buyer/seller',
    `verify_code`      VARCHAR(8) COMMENT '自提核销码(卖家确认用)',
    `status`           TINYINT DEFAULT 0 COMMENT '订单状态(见上方说明)',
    `pickup_location`  VARCHAR(200) COMMENT '自提地点',
    `pickup_time`      DATETIME COMMENT '约定自提时间',
    `complete_time`    DATETIME COMMENT '完成时间',
    `create_time`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_buyer` (`buyer_id`),
    INDEX `idx_seller` (`seller_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_verify_code` (`verify_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单表';

-- ============================================================
-- 配送工单表(仅 deal_type=1 时创建)
-- 状态: 待接单(0) → 已接单/待取货(1) → 已取货/配送中(2) → 已送达(3)
-- ============================================================
CREATE TABLE IF NOT EXISTS `delivery_order` (
    `id`             BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id`       BIGINT NOT NULL COMMENT '关联订单ID',
    `runner_id`      BIGINT COMMENT '交付员ID(接单后填充)',
    `seller_addr`    VARCHAR(200) COMMENT '卖家取货地址',
    `buyer_addr`     VARCHAR(200) NOT NULL COMMENT '买家收货地址(宿舍)',
    `pickup_photo`   VARCHAR(500) COMMENT '取货时拍照存证URL',
    `deliver_photo`  VARCHAR(500) COMMENT '送达时拍照存证URL',
    `status`         TINYINT DEFAULT 0 COMMENT '状态: 0-待接单 1-待取货 2-配送中 3-已送达',
    `delivery_fee`   DECIMAL(10,2) DEFAULT 0 COMMENT '配送费',
    `accept_time`    DATETIME COMMENT '接单时间',
    `pickup_time`    DATETIME COMMENT '取货时间',
    `deliver_time`   DATETIME COMMENT '送达时间',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_order` (`order_id`),
    INDEX `idx_runner` (`runner_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送工单表';

-- ============================================================
-- 物流轨迹表
-- ============================================================
CREATE TABLE IF NOT EXISTS `delivery_track` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `delivery_id`     BIGINT NOT NULL COMMENT '配送工单ID',
    `runner_id`       BIGINT NOT NULL COMMENT '交付员ID',
    `latitude`        DECIMAL(10,7) COMMENT '纬度',
    `longitude`       DECIMAL(10,7) COMMENT '经度',
    `address`         VARCHAR(200) COMMENT '位置描述',
    `action`          VARCHAR(20) NOT NULL COMMENT '动作: accept/pickup/deliver/location',
    `photo_url`       VARCHAR(500) COMMENT '拍照URL',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_delivery` (`delivery_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流轨迹表';

-- ============================================================
-- 评价表
-- ============================================================
CREATE TABLE IF NOT EXISTS `review` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id`      BIGINT NOT NULL COMMENT '订单ID',
    `reviewer_id`   BIGINT NOT NULL COMMENT '评价者ID',
    `target_id`     BIGINT NOT NULL COMMENT '被评价者ID',
    `rating`        TINYINT NOT NULL COMMENT '评分 1-5',
    `content`       VARCHAR(500) COMMENT '评价内容',
    `tags`          JSON COMMENT '评价标签 ["态度好","描述准确"]',
    `status`        TINYINT DEFAULT 1 COMMENT '1-正常 0-已屏蔽',
    `appeal_reason` VARCHAR(500) COMMENT '申诉理由',
    `appeal_status` TINYINT DEFAULT 0 COMMENT '0-无申诉 1-申诉中 2-申诉通过 3-申诉驳回',
    `appeal_time`   DATETIME COMMENT '申诉时间',
    `reply`         VARCHAR(500) COMMENT '被评价者回复',
    `reply_time`    DATETIME COMMENT '回复时间',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_reviewer` (`order_id`, `reviewer_id`),
    INDEX `idx_target` (`target_id`),
    INDEX `idx_appeal_status` (`appeal_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- ============================================================
-- 收货地址表
-- ============================================================
CREATE TABLE IF NOT EXISTS `address` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `contact_name`  VARCHAR(50) NOT NULL COMMENT '联系人姓名',
    `phone`         VARCHAR(20) NOT NULL COMMENT '手机号',
    `building`      VARCHAR(100) NOT NULL COMMENT '楼栋',
    `detail`        VARCHAR(200) NOT NULL COMMENT '宿舍号/详细地址',
    `is_default`    TINYINT DEFAULT 0 COMMENT '是否默认: 0-否 1-是',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- ============================================================
-- 收藏表
-- ============================================================
CREATE TABLE IF NOT EXISTS `favorite` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL,
    `goods_id`    BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`),
    INDEX `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

CREATE TABLE IF NOT EXISTS `browse_history` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL,
    `goods_id`    BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_goods` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览历史表';

-- ============================================================
-- 关注表
-- ============================================================
CREATE TABLE IF NOT EXISTS `follow` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `follower_id`  BIGINT NOT NULL COMMENT '关注者',
    `following_id` BIGINT NOT NULL COMMENT '被关注者',
    `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_follow` (`follower_id`, `following_id`),
    INDEX `idx_following` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注表';

-- ============================================================
-- 纠纷仲裁表
-- ============================================================
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

-- ============================================================
-- 举报投诉表
-- ============================================================
CREATE TABLE IF NOT EXISTS `report` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `reporter_id`  BIGINT NOT NULL COMMENT '举报人',
    `target_type`  VARCHAR(20) NOT NULL COMMENT '目标类型: goods/user/message',
    `target_id`    BIGINT NOT NULL COMMENT '目标ID',
    `reason`       VARCHAR(500) NOT NULL COMMENT '举报原因',
    `evidence`     JSON COMMENT '证据',
    `status`       TINYINT DEFAULT 0 COMMENT '0-待处理 1-已处理 2-已驳回',
    `result`       VARCHAR(500) COMMENT '处理结果',
    `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_target` (`target_type`, `target_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报投诉表';

-- ============================================================
-- 通知消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL COMMENT '接收人',
    `type`        VARCHAR(30) NOT NULL COMMENT '类型: order_status/new_message/system/review_invite/fraud_alert',
    `title`       VARCHAR(200) NOT NULL,
    `content`     VARCHAR(1000),
    `extra`       JSON COMMENT '扩展数据',
    `is_read`     TINYINT DEFAULT 0 COMMENT '0-未读 1-已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

-- ============================================================
-- 系统公告表
-- ============================================================
CREATE TABLE IF NOT EXISTS `announcement` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `title`       VARCHAR(200) NOT NULL,
    `content`     TEXT NOT NULL,
    `type`        VARCHAR(20) DEFAULT 'normal' COMMENT 'normal/important/urgent',
    `status`      TINYINT DEFAULT 1 COMMENT '0-下架 1-发布',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- ============================================================
-- 帖子表 (论坛)
-- ============================================================
CREATE TABLE IF NOT EXISTS `post` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`       BIGINT NOT NULL,
    `title`         VARCHAR(200) NOT NULL,
    `content`       TEXT NOT NULL,
    `category`      VARCHAR(50),
    `images`        JSON,
    `view_count`    INT DEFAULT 0,
    `like_count`    INT DEFAULT 0,
    `comment_count` INT DEFAULT 0,
    `is_top`        TINYINT DEFAULT 0,
    `status`        TINYINT DEFAULT 1 COMMENT '0-删除 1-正常',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_category` (`category`),
    FULLTEXT INDEX `ft_post` (`title`, `content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子表';

-- ============================================================
-- 评论表 (论坛)
-- ============================================================
CREATE TABLE IF NOT EXISTS `comment` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `post_id`     BIGINT NOT NULL,
    `user_id`     BIGINT NOT NULL,
    `parent_id`   BIGINT DEFAULT 0 COMMENT '父评论ID, 0为顶级',
    `content`     VARCHAR(1000) NOT NULL,
    `images`      JSON DEFAULT NULL COMMENT '评论图片',
    `like_count`  INT DEFAULT 0,
    `status`      TINYINT DEFAULT 1 COMMENT '0-已删除 1-正常 2-机审拦截 3-待人审 4-人审违规',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_post` (`post_id`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

CREATE TABLE IF NOT EXISTS `post_favorite` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL,
    `post_id`     BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子收藏表';

-- ============================================================
-- 系统配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_key`   VARCHAR(100) UNIQUE NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(1000) NOT NULL COMMENT '配置值',
    `description`  VARCHAR(200) COMMENT '描述',
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 默认配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('site_name', '校园生态平台', '站点名称'),
('register_enabled', 'true', '是否开放注册'),
('delivery_enabled', 'true', '是否开启配送服务'),
('max_images_per_goods', '9', '每件商品最大图片数');

-- ============================================================
-- 封禁记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ban_record` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `target_type` VARCHAR(20) NOT NULL COMMENT '封禁类型: user/ip',
    `target_value` VARCHAR(100) NOT NULL COMMENT '封禁目标: 用户ID或IP地址',
    `ban_type`    VARCHAR(20) NOT NULL COMMENT '封禁能力: all/trade/message/forum',
    `reason`      VARCHAR(500) NOT NULL COMMENT '封禁原因',
    `ban_until`   DATETIME NOT NULL COMMENT '封禁截止时间',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
    `status`      TINYINT DEFAULT 1 COMMENT '状态: 0-已解除 1-生效中',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_target` (`target_type`, `target_value`),
    INDEX `idx_ban_until` (`ban_until`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='封禁记录表';

-- ============================================================
-- 疯狂星期四抢购记录表
-- ============================================================
-- 疯狂星期四活动表（每周一条记录）
CREATE TABLE IF NOT EXISTS `crazy_thursday` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `week_key`      VARCHAR(20) NOT NULL COMMENT '周标识: 2026-W23',
    `max_slots`     INT DEFAULT 10 COMMENT '最大名额',
    `winner_id`     BIGINT COMMENT '中奖用户ID',
    `status`        TINYINT DEFAULT 0 COMMENT '0-报名中 1-已开奖',
    `draw_time`     DATETIME COMMENT '开奖时间',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_week` (`week_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疯狂星期四活动表';

-- 疯狂星期四报名表
CREATE TABLE IF NOT EXISTS `crazy_thursday_registration` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `week_key`    VARCHAR(20) NOT NULL COMMENT '周标识',
    `user_id`     BIGINT NOT NULL COMMENT '报名用户ID',
    `is_winner`   TINYINT DEFAULT 0 COMMENT '是否中奖',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_week_user` (`week_key`, `user_id`),
    INDEX `idx_week` (`week_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疯狂星期四报名表';

-- ============================================================
-- AI 聊天会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_chat_session` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL COMMENT '用户ID',
    `title`       VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天会话表';

-- ============================================================
-- AI 聊天消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_chat_message` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id`  BIGINT NOT NULL COMMENT '会话ID',
    `role`        VARCHAR(20) NOT NULL COMMENT '角色: user/assistant',
    `content`     TEXT NOT NULL COMMENT '消息内容',
    `sources`     JSON COMMENT '参考来源',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息表';

-- 用户拉黑表
CREATE TABLE IF NOT EXISTS `user_block` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`         BIGINT NOT NULL COMMENT '拉黑者ID',
    `blocked_user_id` BIGINT NOT NULL COMMENT '被拉黑者ID',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_blocked` (`user_id`, `blocked_user_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_blocked_user_id` (`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户拉黑表';

-- ============================================================
-- 敏感词库（内容安全治理：发布时词库同步过滤）
-- ============================================================
CREATE TABLE IF NOT EXISTS `sensitive_word` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `word`        VARCHAR(64) NOT NULL COMMENT '敏感词',
    `category`    VARCHAR(20) NOT NULL COMMENT '类目: 政治/辱骂/色情/广告/谣言/隐私/其他',
    `level`       TINYINT DEFAULT 1 COMMENT '1-拒绝发布 2-替换*** 3-送审',
    `enabled`     TINYINT DEFAULT 1 COMMENT '1-启用 0-停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_word` (`word`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';

-- ============================================================
-- 内容安全审计流水（状态流转全记录，KPI 指标数据源）
-- ============================================================
CREATE TABLE IF NOT EXISTS `content_audit_log` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `target_type`   VARCHAR(20) NOT NULL COMMENT '目标类型: post/comment/message/goods',
    `target_id`     BIGINT NOT NULL COMMENT '目标ID',
    `trigger_type`  TINYINT NOT NULL COMMENT '触发方式: 1-发布词库 2-举报机审 3-人审 4-巡查 5-申诉',
    `content`       VARCHAR(2000) COMMENT '内容快照',
    `hit_words`     VARCHAR(500) COMMENT '命中的敏感词, 逗号分隔',
    `ai_verdict`    TINYINT COMMENT 'AI判定: 0-合规 1-违规 2-不确定',
    `ai_confidence` DECIMAL(4,3) COMMENT 'AI置信度 0-1',
    `ai_reason`     VARCHAR(500) COMMENT 'AI判定理由',
    `final_status`  TINYINT NOT NULL COMMENT '流转后的状态(对应该对象status枚举)',
    `reviewer_id`   BIGINT COMMENT '人工操作者ID, 机器判定为NULL',
    `review_note`   VARCHAR(500) COMMENT '人工备注/修正理由',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_target` (`target_type`, `target_id`),
    INDEX `idx_trigger` (`trigger_type`),
    INDEX `idx_final_status` (`final_status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容安全审计流水';

CREATE TABLE IF NOT EXISTS `login_log` (
    `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username`   VARCHAR(100) NOT NULL COMMENT '尝试登录的用户名(邮箱), 成败均记录',
    `user_id`    BIGINT COMMENT '登录成功的用户ID, 失败为NULL',
    `ip`         VARCHAR(64) COMMENT '客户端IP',
    `user_agent` VARCHAR(255) COMMENT '浏览器User-Agent',
    `success`    TINYINT NOT NULL COMMENT '1-成功 0-失败',
    `message`    VARCHAR(100) COMMENT '结果说明',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_username` (`username`),
    INDEX `idx_ip` (`ip`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录审计日志';

-- ============================================================
-- 初始数据
-- ============================================================
-- 管理员 (密码: admin123)
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`)
VALUES ('admin', '$2b$12$jyG4ejeC1OdIlwVycVF.Yu4p.0DHPUP9aAS.odLhWkPlmiLnLwn2m', '管理员', 1, 1);

-- 示例交付员 (密码: runner123)
INSERT INTO `user` (`username`, `password`, `nickname`, `phone`, `role`, `status`)
VALUES ('runner01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '跑腿小哥', '13800000001', 2, 1);

-- 初始分类
INSERT INTO `category` (`name`, `icon`, `sort_order`, `parent_id`) VALUES
('数码', 'el-icon-monitor', 1, 0),
('教材', 'el-icon-reading', 2, 0),
('生活', 'el-icon-goods', 3, 0),
('服饰', 'el-icon-dress', 4, 0),
('运动', 'el-icon-football', 5, 0),
('美妆', 'el-icon-magic-stick', 6, 0),
('其他', 'el-icon-more', 99, 0);

-- 敏感词种子（本地关键词过滤词库；管理后台可维护，变更即时生效）
INSERT IGNORE INTO `sensitive_word` (`word`, `category`, `level`) VALUES
('代考', '广告', 1),
('代写论文', '广告', 1),
('加微信', '广告', 2),
('办证', '广告', 1),
('枪手代考', '广告', 1),
('傻逼', '辱骂', 1),
('傻吊', '辱骂', 1),
('脑残', '辱骂', 1),
('智障', '辱骂', 1),
('白痴', '辱骂', 1),
('狗东西', '辱骂', 1),
('草泥马', '辱骂', 1),
('操你妈', '辱骂', 1),
('去死吧', '辱骂', 1),
('神经病', '辱骂', 3),
('孤儿', '辱骂', 3),
('垃圾玩意', '辱骂', 1),
('贱人', '辱骂', 1),
('废物点心', '辱骂', 1);

-- 扩充词库：常见脏话（含谐音/变体）、导流、广告替考、诈骗赌博、色情违禁；管理后台可继续维护
INSERT IGNORE INTO `sensitive_word` (`word`, `category`, `level`) VALUES
('煞笔', '辱骂', 1),
('沙比', '辱骂', 1),
('傻比', '辱骂', 1),
('傻B', '辱骂', 1),
('傻叉', '辱骂', 1),
('傻X', '辱骂', 1),
('傻吊', '辱骂', 1),
('傻叼', '辱骂', 1),
('脑瘫', '辱骂', 3),
('弱智', '辱骂', 2),
('蠢货', '辱骂', 2),
('草泥麻', '辱骂', 1),
('操尼玛', '辱骂', 1),
('曹尼玛', '辱骂', 1),
('艹泥马', '辱骂', 1),
('泥马', '辱骂', 2),
('艹你妈', '辱骂', 1),
('草你妈', '辱骂', 1),
('曹你妈', '辱骂', 1),
('干你妈', '辱骂', 1),
('甘你妈', '辱骂', 1),
('你妈的', '辱骂', 1),
('他妈的', '辱骂', 2),
('他吗的', '辱骂', 2),
('妈的', '辱骂', 2),
('妈逼', '辱骂', 1),
('妈批', '辱骂', 1),
('妈蛋', '辱骂', 2),
('玛德', '辱骂', 2),
('尼玛', '辱骂', 2),
('你麻痹', '辱骂', 1),
('妈卖批', '辱骂', 1),
('麻卖批', '辱骂', 1),
('马卖批', '辱骂', 1),
('nmsl', '辱骂', 1),
('tmd', '辱骂', 2),
('tnnd', '辱骂', 2),
('混蛋', '辱骂', 1),
('王八蛋', '辱骂', 1),
('狗屎', '辱骂', 1),
('狗娘养的', '辱骂', 1),
('狗比', '辱骂', 1),
('婊子', '辱骂', 1),
('臭婊子', '辱骂', 1),
('贱货', '辱骂', 1),
('骚货', '辱骂', 1),
('贱婢', '辱骂', 1),
('鸡巴', '辱骂', 1),
('鸡掰', '辱骂', 1),
('滚你妈', '辱骂', 1),
('人渣', '辱骂', 1),
('败类', '辱骂', 1),
('卧槽', '辱骂', 2),
('卧艹', '辱骂', 2),
('我操', '辱骂', 2),
('我草', '辱骂', 2),
('握草', '辱骂', 2),
('我擦', '辱骂', 2),
('我靠', '辱骂', 2),
('我日', '辱骂', 2),
('沃日', '辱骂', 2),
('特么', '辱骂', 2),
('沙雕', '辱骂', 2),
('菜鸡', '辱骂', 2),
('菜逼', '辱骂', 1),
('饭桶', '辱骂', 2),
('滚蛋', '辱骂', 2),
('加v', '导流', 2),
('加V', '导流', 2),
('加vx', '导流', 2),
('加vxin', '导流', 2),
('加威', '导流', 2),
('加我微信', '导流', 1),
('微信联系', '导流', 1),
('威信', '导流', 2),
('加扣扣', '导流', 2),
('加QQ', '导流', 2),
('薇信', '导流', 2),
('代刷', '广告', 1),
('刷单', '广告', 1),
('刷好评', '广告', 1),
('代课', '广告', 1),
('代打卡', '广告', 1),
('代写作业', '广告', 1),
('论文代写', '广告', 1),
('代写', '广告', 3),
('出售答案', '广告', 1),
('答案出售', '广告', 1),
('四六级答案', '广告', 1),
('考试答案', '广告', 1),
('包过', '广告', 1),
('保过', '广告', 1),
('诚招代理', '广告', 1),
('日赚', '广告', 1),
('轻松赚钱', '广告', 1),
('兼职日结', '广告', 1),
('刷单返利', '诈骗', 1),
('花呗套现', '诈骗', 1),
('信用卡套现', '诈骗', 1),
('买卖身份证', '违法', 1),
('杀猪盘', '诈骗', 1),
('荐股', '诈骗', 1),
('传销', '诈骗', 1),
('资金盘', '诈骗', 1),
('网赌', '赌博', 1),
('赌球', '赌博', 1),
('博彩', '赌博', 1),
('时时彩', '赌博', 1),
('六合彩', '赌博', 1),
('赌场', '赌博', 1),
('赌博', '赌博', 1),
('卖淫', '色情', 1),
('嫖娼', '色情', 1),
('裸聊', '色情', 1),
('援交', '色情', 1),
('陪睡', '色情', 1),
('招嫖', '色情', 1),
('色情服务', '色情', 1),
('黄片', '色情', 1),
('包夜', '色情', 3),
('上门服务', '色情', 3),
('特殊服务', '色情', 3),
('迷药', '违禁', 1),
('春药', '违禁', 1),
('冰毒', '违禁', 1),
('大麻', '违禁', 1),
('海洛因', '违禁', 1),
('摇头丸', '违禁', 1),
('K粉', '违禁', 1),
('毒品', '违禁', 1),
('买卖枪支', '违禁', 1),
('气枪', '违禁', 1),
('管制刀具', '违禁', 1);
