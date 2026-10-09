package com.campus.common.moderation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.entity.ContentAuditLog;
import com.campus.common.mapper.ContentAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容安全审计流水写入与查询。每次状态流转必须留痕，
 * KPI（识别准确率、人审减负率）从本表统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** 触发方式常量 */
    public static final int TRIGGER_PUBLISH = 1;   // 发布词库
    public static final int TRIGGER_REPORT_AI = 2; // 举报机审
    public static final int TRIGGER_HUMAN = 3;     // 人审
    public static final int TRIGGER_PATROL = 4;    // 巡查
    public static final int TRIGGER_APPEAL = 5;    // 申诉

    private final ContentAuditLogMapper mapper;

    /** 记一条流水（内容快照超 2000 字截断）。 */
    public void record(ContentAuditLog entry) {
        try {
            if (entry.getContent() != null && entry.getContent().length() > 2000) {
                entry.setContent(entry.getContent().substring(0, 2000));
            }
            if (entry.getHitWords() != null && entry.getHitWords().length() > 500) {
                entry.setHitWords(entry.getHitWords().substring(0, 500));
            }
            if (entry.getAiReason() != null && entry.getAiReason().length() > 500) {
                entry.setAiReason(entry.getAiReason().substring(0, 500));
            }
            mapper.insert(entry);
        } catch (Exception e) {
            // 流水写入失败不阻断业务，但要显式告警（审计完整性依赖此表）
            log.error("审计流水写入失败: target={}/{}", entry.getTargetType(), entry.getTargetId(), e);
        }
    }

    /** 某对象的全部流水，按时间倒序。 */
    public List<ContentAuditLog> listByTarget(String targetType, Long targetId) {
        return mapper.selectList(new LambdaQueryWrapper<ContentAuditLog>()
                .eq(ContentAuditLog::getTargetType, targetType)
                .eq(ContentAuditLog::getTargetId, targetId)
                .orderByDesc(ContentAuditLog::getId));
    }

    /** 按流水 ID 查询。 */
    public ContentAuditLog getById(Long id) {
        return mapper.selectById(id);
    }

    public ContentAuditLogMapper mapper() {
        return mapper;
    }
}
