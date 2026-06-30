package com.campus.app.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.app.entity.Announcement;
import com.campus.common.result.R;
import com.campus.app.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 用户端：获取公告列表 */
    @GetMapping
    public R<Page<Announcement>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(announcementService.listActive(page, size));
    }

    /** 管理端：获取全部公告 */
    @GetMapping("/all")
    @SaCheckRole("ADMIN")
    public R<Page<Announcement>> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(announcementService.listAll(page, size));
    }

    /** 管理端：创建公告 */
    @PostMapping
    @SaCheckRole("ADMIN")
    public R<Announcement> create(@RequestBody Announcement announcement) {
        return R.ok(announcementService.createAnnouncement(announcement));
    }

    /** 管理端：更新公告 */
    @PutMapping("/{id}")
    @SaCheckRole("ADMIN")
    public R<Void> update(@PathVariable Long id, @RequestBody Announcement announcement) {
        announcement.setId(id);
        announcementService.updateAnnouncement(announcement);
        return R.ok();
    }

    /** 管理端：删除公告 */
    @DeleteMapping("/{id}")
    @SaCheckRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        announcementService.deleteAnnouncement(id);
        return R.ok();
    }
}
