package com.campus.common.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.entity.Notification;
import com.campus.common.result.R;
import com.campus.common.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 站内通知接口。消息中心页面使用，操作对象均为当前登录用户本人的通知。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 分页查询我的通知列表（按创建时间倒序） */
    @GetMapping
    public R<Page<Notification>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(notificationService.getMyNotifications(StpUtil.getLoginIdAsLong(), page, size));
    }

    /** 把一条通知标记为已读 */
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markAsRead(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    /** 把我的全部未读通知标记为已读 */
    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllAsRead(StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    /** 查询未读通知数，返回 {"count": n}，供前端红点展示 */
    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("count", notificationService.getUnreadCount(StpUtil.getLoginIdAsLong())));
    }
}
