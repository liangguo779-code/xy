-- ============================================================
-- 演示 SQL ③：社交与 AI 模块
-- 核心表：post → comment → chat_session → chat_message → favorite → ai_chat_session
-- ============================================================

CREATE DATABASE IF NOT EXISTS campus DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus;

-- 1. 论坛帖子表（关联 user）
CREATE TABLE IF NOT EXISTS `post` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`       BIGINT NOT NULL,
    `title`         VARCHAR(200) NOT NULL,
    `content`       TEXT NOT NULL,
    `category`      VARCHAR(50) COMMENT '分类',
    `images`        JSON,
    `view_count`    INT DEFAULT 0,
    `like_count`    INT DEFAULT 0,
    `comment_count` INT DEFAULT 0,
    `is_top`        TINYINT DEFAULT 0 COMMENT '是否置顶',
    `status`        TINYINT DEFAULT 1 COMMENT '0-删除 1-正常',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_category` (`category`),
    FULLTEXT INDEX `ft_post` (`title`, `content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子表';

-- 2. 评论表（关联 post + user，支持嵌套回复）
CREATE TABLE IF NOT EXISTS `comment` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `post_id`     BIGINT NOT NULL,
    `user_id`     BIGINT NOT NULL,
    `parent_id`   BIGINT DEFAULT 0 COMMENT '父评论ID, 0为顶级',
    `content`     VARCHAR(1000) NOT NULL,
    `like_count`  INT DEFAULT 0,
    `status`      TINYINT DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_post` (`post_id`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- 3. 聊天会话表（关联 goods + user）
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `goods_id`    BIGINT NOT NULL COMMENT '关联商品ID',
    `buyer_id`    BIGINT NOT NULL COMMENT '买家ID',
    `seller_id`   BIGINT NOT NULL COMMENT '卖家ID',
    `last_msg`    VARCHAR(500) COMMENT '最后消息预览',
    `last_time`   DATETIME COMMENT '最后消息时间',
    `status`      TINYINT DEFAULT 1 COMMENT '0-关闭 1-活跃',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_goods_buyer` (`goods_id`, `buyer_id`),
    INDEX `idx_buyer` (`buyer_id`),
    INDEX `idx_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 4. 聊天消息表（关联 chat_session + user）
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id`  BIGINT NOT NULL COMMENT '会话ID',
    `sender_id`   BIGINT NOT NULL COMMENT '发送者ID',
    `msg_type`    TINYINT DEFAULT 0 COMMENT '0-文本 1-图片 2-系统 3-快捷操作',
    `content`     VARCHAR(2000) NOT NULL,
    `extra`       TEXT COMMENT '扩展数据',
    `is_read`     TINYINT DEFAULT 0 COMMENT '0-未读 1-已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session` (`session_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- 5. 收藏表（关联 user + goods）
CREATE TABLE IF NOT EXISTS `favorite` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL,
    `goods_id`    BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`),
    INDEX `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- 6. AI 聊天会话表（关联 user）
CREATE TABLE IF NOT EXISTS `ai_chat_session` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL COMMENT '用户ID',
    `title`       VARCHAR(200) NOT NULL DEFAULT '新对话',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天会话表';

-- 7. AI 聊天消息表（关联 ai_chat_session）
CREATE TABLE IF NOT EXISTS `ai_chat_message` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id`  BIGINT NOT NULL COMMENT '会话ID',
    `role`        VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    `content`     TEXT NOT NULL,
    `sources`     JSON COMMENT '参考来源',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI聊天消息表';

-- ========== 外键约束 ==========
ALTER TABLE `post`            ADD CONSTRAINT `fk_post_user`          FOREIGN KEY (`user_id`)    REFERENCES `user`(`id`)           ON DELETE CASCADE;
ALTER TABLE `comment`         ADD CONSTRAINT `fk_comment_post`       FOREIGN KEY (`post_id`)    REFERENCES `post`(`id`)           ON DELETE CASCADE;
ALTER TABLE `comment`         ADD CONSTRAINT `fk_comment_user`       FOREIGN KEY (`user_id`)    REFERENCES `user`(`id`)           ON DELETE CASCADE;
ALTER TABLE `chat_session`    ADD CONSTRAINT `fk_chat_session_buyer`  FOREIGN KEY (`buyer_id`)  REFERENCES `user`(`id`)           ON DELETE CASCADE;
ALTER TABLE `chat_session`    ADD CONSTRAINT `fk_chat_session_seller` FOREIGN KEY (`seller_id`) REFERENCES `user`(`id`)           ON DELETE CASCADE;
ALTER TABLE `chat_message`    ADD CONSTRAINT `fk_chat_msg_session`    FOREIGN KEY (`session_id`) REFERENCES `chat_session`(`id`)   ON DELETE CASCADE;
ALTER TABLE `favorite`        ADD CONSTRAINT `fk_fav_user`           FOREIGN KEY (`user_id`)    REFERENCES `user`(`id`)           ON DELETE CASCADE;
ALTER TABLE `favorite`        ADD CONSTRAINT `fk_fav_goods`          FOREIGN KEY (`goods_id`)   REFERENCES `goods`(`id`)          ON DELETE CASCADE;
ALTER TABLE `ai_chat_session` ADD CONSTRAINT `fk_ai_session_user`    FOREIGN KEY (`user_id`)    REFERENCES `user`(`id`)           ON DELETE CASCADE;
ALTER TABLE `ai_chat_message` ADD CONSTRAINT `fk_ai_msg_session`     FOREIGN KEY (`session_id`) REFERENCES `ai_chat_session`(`id`) ON DELETE CASCADE;

-- ========== 示例数据 ==========
INSERT INTO `post` (`user_id`, `title`, `content`, `category`) VALUES
(2, '求购二手自行车', '毕业在即，求购一辆二手自行车，价格200以内', '生活'),
(3, '校园快递代取', '有偿代取快递，每单3元，晚上统一配送', '生活'),
(2, '出二手iPad 2021', '考研结束，出iPad 2021款，128G', '数码');

INSERT INTO `comment` (`post_id`, `user_id`, `parent_id`, `content`) VALUES
(1, 3, 0, '我有自行车，在学苑A栋楼下，可以来看看'),
(1, 2, 1, '好的，下午去看'),
(2, 2, 0, '怎么联系？我需要代取');

INSERT INTO `chat_session` (`goods_id`, `buyer_id`, `seller_id`, `last_msg`, `last_time`) VALUES
(1, 3, 2, '你好，手机还在吗？', NOW()),
(2, 3, 2, '教材我要了', NOW());

INSERT INTO `chat_message` (`session_id`, `sender_id`, `msg_type`, `content`) VALUES
(1, 3, 0, '你好，手机还在吗？'),
(1, 2, 0, '在的，随时可以看'),
(2, 3, 0, '教材我要了，怎么交易？'),
(2, 2, 0, '学苑A栋楼下自提');
