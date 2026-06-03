package com.campus.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.Announcement;
import com.campus.common.mapper.AnnouncementMapper;
import com.campus.common.service.AnnouncementService;
import org.springframework.stereotype.Service;

@Service
public class AnnouncementServiceImpl extends ServiceImpl<AnnouncementMapper, Announcement> implements AnnouncementService {

    @Override
    public Page<Announcement> listActive(int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, 1)
                .orderByDesc(Announcement::getCreateTime));
    }

    @Override
    public Page<Announcement> listAll(int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getCreateTime));
    }

    @Override
    public Announcement createAnnouncement(Announcement announcement) {
        announcement.setStatus(1);
        save(announcement);
        return announcement;
    }

    @Override
    public void updateAnnouncement(Announcement announcement) {
        updateById(announcement);
    }

    @Override
    public void deleteAnnouncement(Long id) {
        removeById(id);
    }
}
