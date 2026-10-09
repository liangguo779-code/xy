package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Follow;
import com.campus.trade.mapper.FollowMapper;
import com.campus.trade.service.FollowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** 用户关注服务实现，基于 follow 表维护粉丝/关注关系 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    /** 关注用户，禁止关注自己，重复关注幂等返回 */
    @Override
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) throw new BusinessException("不能关注自己");
        Long count = count(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId).eq(Follow::getFollowingId, followingId));
        if (count > 0) return;

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        save(follow);
    }

    /** 取消关注，不存在该记录时静默成功 */
    @Override
    public void unfollow(Long followerId, Long followingId) {
        remove(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId).eq(Follow::getFollowingId, followingId));
    }

    /** 判断 follower 是否已关注 following */
    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        return count(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId).eq(Follow::getFollowingId, followingId)) > 0;
    }

    /** 获取该用户关注的人的 ID 列表 */
    @Override
    public List<Long> getFollowingIds(Long userId) {
        return list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId))
                .stream().map(Follow::getFollowingId).collect(Collectors.toList());
    }

    /** 获取该用户粉丝的 ID 列表 */
    @Override
    public List<Long> getFollowerIds(Long userId) {
        return list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId))
                .stream().map(Follow::getFollowerId).collect(Collectors.toList());
    }

    /** 获取关注数 */
    @Override
    public long getFollowingCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
    }

    /** 获取粉丝数 */
    @Override
    public long getFollowerCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId));
    }
}
