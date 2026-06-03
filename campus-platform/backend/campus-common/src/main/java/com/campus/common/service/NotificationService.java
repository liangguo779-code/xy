package com.campus.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.common.entity.Notification;

public interface NotificationService extends IService<Notification> {
    void send(Long userId, String type, String title, String content, String extra);
    Page<Notification> getMyNotifications(Long userId, int page, int size);
    void markAsRead(Long userId, Long notificationId);
    void markAllAsRead(Long userId);
    long getUnreadCount(Long userId);
}
