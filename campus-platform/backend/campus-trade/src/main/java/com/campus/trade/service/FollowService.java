package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Follow;

import java.util.List;

/** 用户关注服务，管理粉丝/关注关系与计数 */
public interface FollowService extends IService<Follow> {
    /** 关注用户 */
    void follow(Long followerId, Long followingId);
    /** 取消关注 */
    void unfollow(Long followerId, Long followingId);
    /** 判断 follower 是否已关注 following */
    boolean isFollowing(Long followerId, Long followingId);
    /** 获取该用户关注的人的 ID 列表 */
    List<Long> getFollowingIds(Long userId);
    /** 获取该用户粉丝的 ID 列表 */
    List<Long> getFollowerIds(Long userId);
    /** 获取关注数 */
    long getFollowingCount(Long userId);
    /** 获取粉丝数 */
    long getFollowerCount(Long userId);
}
