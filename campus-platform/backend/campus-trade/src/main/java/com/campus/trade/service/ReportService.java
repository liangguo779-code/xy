package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Report;

/** 举报服务，处理内容举报的提交、查询与平台处理 */
public interface ReportService extends IService<Report> {
    /** 提交举报，targetType 限 goods/user/message/post/comment，同一用户对同目标重复举报会被拒绝 */
    void createReport(Long reporterId, String targetType, Long targetId, String reason, String evidence);
    /** 分页查询举报列表（管理端），status 为 null 时查全部状态 */
    Page<Report> listReports(Integer status, int page, int size);
    /** 分页查询我提交的举报，status 为 null 时查全部状态 */
    Page<Report> listMyReports(Long reporterId, Integer status, int page, int size);
    /** 处理举报：status 只能为 1-已处理 或 2-已驳回，且只能处理待处理的举报 */
    void handleReport(Long reportId, String result, int status);
}
