package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Follow;

import java.util.List;

public interface FollowService extends IService<Follow> {
    void follow(Long followerId, Long followingId);
    void unfollow(Long followerId, Long followingId);
    boolean isFollowing(Long followerId, Long followingId);
    List<Long> getFollowingIds(Long userId);
    List<Long> getFollowerIds(Long userId);
    long getFollowingCount(Long userId);
    long getFollowerCount(Long userId);
}
