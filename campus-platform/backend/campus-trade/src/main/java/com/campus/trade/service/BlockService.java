package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Block;
import com.campus.common.dto.UserVO;

/** 用户拉黑服务，管理拉黑关系并提供消息/内容过滤所需的拉黑判断 */
public interface BlockService extends IService<Block> {

    /** 拉黑用户 */
    void blockUser(Long userId, Long blockedUserId);

    /** 取消拉黑 */
    void unblockUser(Long userId, Long blockedUserId);

    /** 检查是否被拉黑 */
    boolean isBlocked(Long userId, Long targetUserId);

    /** 检查是否被对方拉黑 */
    boolean isBlockedBy(Long userId, Long targetUserId);

    /** 获取拉黑列表 */
    Page<UserVO> getBlockList(Long userId, int page, int size);
}
