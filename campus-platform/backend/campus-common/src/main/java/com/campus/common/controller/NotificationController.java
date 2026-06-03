package com.campus.common.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.entity.Notification;
import com.campus.common.result.R;
import com.campus.common.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public R<Page<Notification>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(notificationService.getMyNotifications(StpUtil.getLoginIdAsLong(), page, size));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markAsRead(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllAsRead(StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        return R.ok(Map.of("count", notificationService.getUnreadCount(StpUtil.getLoginIdAsLong())));
    }
}
