-- ============================================================
-- 演示 SQL ①：用户与基础模块
-- 核心表：user → address / notification / announcement / sys_config / ban_record
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus;

-- 1. 用户表（核心主表）
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username`    VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    `nickname`    VARCHAR(50) COMMENT '昵称',
    `avatar`      VARCHAR(255) COMMENT '头像URL',
    `phone`       VARCHAR(20) COMMENT '手机号',
    `dormitory`   VARCHAR(100) COMMENT '宿舍地址',
    `role`        TINYINT DEFAULT 0 COMMENT '角色: 0-普通 1-管理员 2-交付员',
    `status`      TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-正常',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 收货地址表（关联 user）
CREATE TABLE IF NOT EXISTS `address` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `contact_name`  VARCHAR(50) NOT NULL COMMENT '联系人',
    `phone`         VARCHAR(20) NOT NULL COMMENT '手机号',
    `building`      VARCHAR(100) NOT NULL COMMENT '楼栋',
    `detail`        VARCHAR(200) NOT NULL COMMENT '宿舍号/详细地址',
    `is_default`    TINYINT DEFAULT 0 COMMENT '是否默认: 0-否 1-是',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- 3. 通知消息表（关联 user）
CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL COMMENT '接收人',
    `type`        VARCHAR(30) NOT NULL COMMENT '类型: order_status/new_message/system/review_invite/fraud_alert',
    `title`       VARCHAR(200) NOT NULL,
    `content`     VARCHAR(1000),
    `extra`       JSON COMMENT '扩展数据',
    `is_read`     TINYINT DEFAULT 0 COMMENT '0-未读 1-已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

-- 4. 系统公告表
CREATE TABLE IF NOT EXISTS `announcement` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `title`       VARCHAR(200) NOT NULL,
    `content`     TEXT NOT NULL,
    `type`        VARCHAR(20) DEFAULT 'normal' COMMENT 'normal/important/urgent',
    `status`      TINYINT DEFAULT 1 COMMENT '0-下架 1-发布',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- 5. 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `config_key`   VARCHAR(100) UNIQUE NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(1000) NOT NULL COMMENT '配置值',
    `description`  VARCHAR(200) COMMENT '描述',
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 6. 封禁记录表（关联 user）
CREATE TABLE IF NOT EXISTS `ban_record` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `target_type`  VARCHAR(20) NOT NULL COMMENT '封禁类型: user/ip',
    `target_value` VARCHAR(100) NOT NULL COMMENT '封禁目标: 用户ID或IP',
    `ban_type`     VARCHAR(20) NOT NULL COMMENT '封禁范围: all/trade/message/forum',
    `reason`       VARCHAR(500) NOT NULL,
    `ban_until`    DATETIME NOT NULL COMMENT '封禁截止时间',
    `operator_id`  BIGINT NOT NULL COMMENT '操作人ID',
    `status`       TINYINT DEFAULT 1 COMMENT '0-已解除 1-生效中',
    `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_target` (`target_type`, `target_value`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='封禁记录表';

-- ========== 外键约束 ==========
ALTER TABLE `address`      ADD CONSTRAINT `fk_address_user`      FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE;
ALTER TABLE `notification` ADD CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE;

-- ========== 示例数据 ==========
INSERT INTO `user` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', '$2b$12$jyG4ejeC1OdIlwVycVF.Yu4p.0DHPUP9aAS.odLhWkPlmiLnLwn2m', '管理员', 1, 1),
('zhangsan', '$2b$12$jyG4ejeC1OdIlwVycVF.Yu4p.0DHPUP9aAS.odLhWkPlmiLnLwn2m', '张三', 0, 1),
('lisi', '$2b$12$jyG4ejeC1OdIlwVycVF.Yu4p.0DHPUP9aAS.odLhWkPlmiLnLwn2m', '李四', 0, 1);

INSERT INTO `address` (`user_id`, `contact_name`, `phone`, `building`, `detail`, `is_default`) VALUES
(2, '张三', '13800000001', '学苑A栋', '501室', 1),
(3, '李四', '13800000002', '学苑B栋', '302室', 1);

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('site_name', '校园生态平台', '站点名称'),
('register_enabled', 'true', '是否开放注册'),
('delivery_enabled', 'true', '是否开启配送服务'),
('max_images_per_goods', '9', '每件商品最大图片数');
