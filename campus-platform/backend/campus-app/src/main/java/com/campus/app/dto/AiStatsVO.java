package com.campus.app.dto;

import lombok.Data;

import java.io.Serializable;

/** AI 模块统计信息，供管理后台概览展示 */
@Data
public class AiStatsVO implements Serializable {

    /** AI 对话会话总数 */
    private Long totalSessions;
}
