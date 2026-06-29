package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Review;
import com.campus.trade.mapper.ReviewMapper;
import com.campus.trade.service.AdminReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements AdminReviewService {

    private final ReviewMapper reviewMapper;

    @Override
    public Page<Review> listAppeals(Integer appealStatus, int page, int size) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        if (appealStatus != null) {
            wrapper.eq(Review::getAppealStatus, appealStatus);
        } else {
            wrapper.gt(Review::getAppealStatus, 0);
        }
        wrapper.orderByDesc(Review::getAppealTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void handleAppeal(Long reviewId, Long adminId, boolean approved, String result) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (review.getAppealStatus() == null || review.getAppealStatus() != 1) {
            throw new BusinessException("该评价没有待处理的申诉");
        }
        if (approved) {
            review.setAppealStatus(2);
            review.setStatus(0);
        } else {
            review.setAppealStatus(3);
        }
        reviewMapper.updateById(review);
    }

    @Override
    @Transactional
    public void updateStatus(Long reviewId, int status) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        review.setStatus(status);
        reviewMapper.updateById(review);
    }

    @Override
    public long[] getStats() {
        long total = count();
        long appealed = count(new LambdaQueryWrapper<Review>().eq(Review::getAppealStatus, 1));
        long hidden = count(new LambdaQueryWrapper<Review>().eq(Review::getStatus, 0));
        return new long[]{total, appealed, hidden};
    }
}
