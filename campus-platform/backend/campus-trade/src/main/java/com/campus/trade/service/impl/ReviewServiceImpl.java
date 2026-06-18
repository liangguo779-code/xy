package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.entity.Order;
import com.campus.trade.entity.Review;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.mapper.ReviewMapper;
import com.campus.trade.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;

    @Override
    public void createReview(Long reviewerId, CreateReviewReq req) {
        Order order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != OrderStatus.COMPLETED.getCode()) {
            throw new BusinessException("订单未完成，不能评价");
        }
        if (!order.getBuyerId().equals(reviewerId) && !order.getSellerId().equals(reviewerId)) {
            throw new BusinessException(403, "无权评价此订单");
        }

        // 检查是否已评价
        Long count = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, req.getOrderId())
                        .eq(Review::getReviewerId, reviewerId)
        );
        if (count > 0) {
            throw new BusinessException("已评价，不能重复评价");
        }

        Long targetId = order.getBuyerId().equals(reviewerId)
                ? order.getSellerId() : order.getBuyerId();

        Review review = new Review();
        review.setOrderId(req.getOrderId());
        review.setReviewerId(reviewerId);
        review.setTargetId(targetId);
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setTags(req.getTags());
        save(review);
    }

    @Override
    @Transactional
    public void updateReview(Long reviewerId, Long reviewId, CreateReviewReq req) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (!review.getReviewerId().equals(reviewerId)) {
            throw new BusinessException(403, "无权修改此评价");
        }
        // 24小时内可修改
        if (review.getCreateTime().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new BusinessException("评价已超过24小时，无法修改");
        }
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setTags(req.getTags());
        reviewMapper.updateById(review);
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewerId, Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (!review.getReviewerId().equals(reviewerId)) {
            throw new BusinessException(403, "无权删除此评价");
        }
        // 24小时内可删除
        if (review.getCreateTime().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new BusinessException("评价已超过24小时，无法删除");
        }
        reviewMapper.deleteById(reviewId);
    }

    @Override
    public List<Review> getReviewsForUser(Long targetId) {
        return list(new LambdaQueryWrapper<Review>()
                .eq(Review::getTargetId, targetId)
                .orderByDesc(Review::getCreateTime));
    }

    @Override
    public List<Review> getReviewsByOrder(Long orderId) {
        return list(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId));
    }

    @Override
    public Double getAverageRating(Long userId) {
        List<Review> reviews = getReviewsForUser(userId);
        if (reviews.isEmpty()) return null;
        return reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
    }
}
