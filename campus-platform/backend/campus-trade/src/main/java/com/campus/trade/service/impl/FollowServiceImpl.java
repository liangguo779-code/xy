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

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

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

    @Override
    public void unfollow(Long followerId, Long followingId) {
        remove(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId).eq(Follow::getFollowingId, followingId));
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        return count(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId).eq(Follow::getFollowingId, followingId)) > 0;
    }

    @Override
    public List<Long> getFollowingIds(Long userId) {
        return list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId))
                .stream().map(Follow::getFollowingId).collect(Collectors.toList());
    }

    @Override
    public List<Long> getFollowerIds(Long userId) {
        return list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId))
                .stream().map(Follow::getFollowerId).collect(Collectors.toList());
    }

    @Override
    public long getFollowingCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
    }

    @Override
    public long getFollowerCount(Long userId) {
        return count(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowingId, userId));
    }
}
