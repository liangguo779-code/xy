package com.campus.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ES 同步事务性发件箱实体，对应 es_sync_outbox 表。
 *
 * <p>业务表变更与本表写入在同一 DB 事务内完成（Transactional Outbox）：
 * 只要业务提交成功，同步意图一定落库；进程崩溃后由 {@code EsOutboxService} 轮询补做，
 * 不会像"直接调 ES"那样在崩溃时丢数据。
 *
 * <p>同一实体只保留最新意图一行（{@code uk_biz} 唯一键 + ON DUPLICATE KEY UPDATE），
 * 重放时重新读库拉齐到当前状态，天然幂等，不存快照。
 */
@Data
@TableName("es_sync_outbox")
public class EsSyncOutbox {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型：goods-商品 post-帖子，对应 {@code EsSyncHandler.bizType()} */
    private String bizType;

    /** 业务主键 ID（商品 ID / 帖子 ID） */
    private Long bizId;

    /** 同步意图：index-重建文档，delete-移除文档 */
    private String op;

    /** 0-待同步 1-已同步 */
    private Integer status;

    /** 失败重试次数，配合 nextRetryAt 做指数退避 */
    private Integer retryCount;

    /** 下次可重试时间，退避窗口内不被轮询捞起 */
    private LocalDateTime nextRetryAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
