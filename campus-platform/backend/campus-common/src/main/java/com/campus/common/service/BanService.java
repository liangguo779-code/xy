package com.campus.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.common.entity.BanRecord;

public interface BanService extends IService<BanRecord> {

    /**
     * 封禁用户
     * @param userId 被封禁用户ID
     * @param banType 封禁类型: all/trade/message/forum
     * @param reason 原因
     * @param days 封禁天数
     * @param operatorId 操作人ID
     */
    void banUser(Long userId, String banType, String reason, int days, Long operatorId);

    /**
     * 封禁IP
     */
    void banIp(String ip, String banType, String reason, int days, Long operatorId);

    /**
     * 解除封禁
     */
    void unban(Long recordId, Long operatorId);

    /**
     * 检查用户是否被封禁（指定能力）
     * @return null表示未封禁，否则返回封禁记录
     */
    BanRecord checkUserBan(Long userId, String banType);

    /**
     * 检查IP是否被封禁
     */
    BanRecord checkIpBan(String ip, String banType);

    /**
     * 获取封禁记录列表
     */
    Page<BanRecord> listBanRecords(String targetType, Integer status, int page, int size);

    /**
     * 清理过期封禁（定时任务调用）
     */
    void cleanExpiredBans();
}
