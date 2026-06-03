package com.campus.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.common.entity.Announcement;

public interface AnnouncementService extends IService<Announcement> {
    Page<Announcement> listActive(int page, int size);
    Page<Announcement> listAll(int page, int size);
    Announcement createAnnouncement(Announcement announcement);
    void updateAnnouncement(Announcement announcement);
    void deleteAnnouncement(Long id);
}
