package com.campus.app.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.app.entity.Announcement;

/** 公告管理服务，提供公告的分页查询与增删改能力 */
public interface AnnouncementService extends IService<Announcement> {

    /** 分页查询已上架公告（用户端） */
    Page<Announcement> listActive(int page, int size);

    /** 分页查询全部公告（管理端，含已下架） */
    Page<Announcement> listAll(int page, int size);

    /** 创建公告，默认置为上架状态 */
    Announcement createAnnouncement(Announcement announcement);

    /** 更新公告 */
    void updateAnnouncement(Announcement announcement);

    /** 删除公告 */
    void deleteAnnouncement(Long id);
}
