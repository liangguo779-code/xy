package com.campus.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.Notification;
import com.campus.common.mapper.NotificationMapper;
import com.campus.common.service.NotificationService;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务实现。所有写操作都限定 userId 归属，防止越权标记他人通知。
 */
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    /** 落库一条未读通知 */
    @Override
    public void send(Long userId, String type, String title, String content, String extra) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setExtra(extra);
        n.setIsRead(0);
        save(n);
    }

    /** 按创建时间倒序分页拉取本人通知 */
    @Override
    public Page<Notification> getMyNotifications(Long userId, int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime));
    }

    /** 单条已读：id 与 userId 双条件，避免改到他人的通知 */
    @Override
    public void markAsRead(Long userId, Long notificationId) {
        update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
    }

    /** 全部已读：只更新未读记录，减少无效写 */
    @Override
    public void markAllAsRead(Long userId) {
        update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public long getUnreadCount(Long userId) {
        return count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }
}
