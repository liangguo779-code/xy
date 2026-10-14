package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.EsSyncOutbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ES 同步发件箱 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供。
 *
 * <p>{@link #upsert} 是关键：同一 {@code (bizType, bizId)} 的重复记录会被压缩成
 * 一行"最新意图"（覆盖 op、重置重试状态），避免浏览量等高频写把表撑爆。
 * 该语句参与调用方当前事务，与业务写同生共死。
 */
@Mapper
public interface EsSyncOutboxMapper extends BaseMapper<EsSyncOutbox> {

    /**
     * 写入/压缩同步意图。已有待同步行时覆盖为最新 op 并重置重试状态。
     *
     * <p>必须在业务同一事务内调用，才能保证"业务提交 ⇔ 同步意图落库"。
     *
     * @param bizType 业务类型 goods/post
     * @param bizId   业务主键 ID
     * @param op      同步意图 index/delete
     * @return 影响行数
     */
    @Insert("INSERT INTO es_sync_outbox " +
            "(biz_type, biz_id, op, status, retry_count, next_retry_at, create_time, update_time) " +
            "VALUES (#{bizType}, #{bizId}, #{op}, 0, 0, NOW(), NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "op = VALUES(op), status = 0, retry_count = 0, next_retry_at = NOW(), update_time = NOW()")
    int upsert(@Param("bizType") String bizType, @Param("bizId") Long bizId, @Param("op") String op);
}
