package com.campus.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.common.entity.Notification;

/**
 * 站内通知服务。业务模块调用 send 投递通知，消息中心接口按用户查询/标记已读。
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 发送一条站内通知（初始为未读）
     *
     * @param userId  接收用户 ID
     * @param type    通知类型，见 Notification.type 取值
     * @param extra   扩展 JSON（可为 null），供前端跳转使用
     */
    void send(Long userId, String type, String title, String content, String extra);

    /**
     * 分页查询用户的通知列表，按创建时间倒序
     */
    Page<Notification> getMyNotifications(Long userId, int page, int size);

    /** 把指定通知标记为已读，仅能操作本人的通知 */
    void markAsRead(Long userId, Long notificationId);

    /** 把用户全部未读通知批量标记为已读 */
    void markAllAsRead(Long userId);

    /** 统计用户未读通知数，用于消息中心红点 */
    long getUnreadCount(Long userId);
}
