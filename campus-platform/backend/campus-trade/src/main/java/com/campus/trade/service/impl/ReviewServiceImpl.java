package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

/** 评价服务实现，覆盖评价的发表/修改/删除、申诉与回复 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;

    /** 发表评价：仅已完成订单的买卖双方可评，且每人每单限评一次 */
    @Override
    @Transactional
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
        review.setStatus(1);
        review.setAppealStatus(0);
        save(review);
    }

    /** 修改自己的评价，仅发布后 24 小时内可改 */
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
        // 超过 24 小时不允许修改，保证评价可信度
        if (review.getCreateTime().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new BusinessException("评价已超过24小时，无法修改");
        }
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setTags(req.getTags());
        reviewMapper.updateById(review);
    }

    /** 删除自己的评价，仅发布后 24 小时内可删 */
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
        // 与修改一致，超过 24 小时不允许删除
        if (review.getCreateTime().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new BusinessException("评价已超过24小时，无法删除");
        }
        reviewMapper.deleteById(reviewId);
    }

    /** 查询某用户收到的正常状态评价，按时间倒序 */
    @Override
    public List<Review> getReviewsForUser(Long targetId) {
        return list(new LambdaQueryWrapper<Review>()
                .eq(Review::getTargetId, targetId)
                .eq(Review::getStatus, 1)
                .orderByDesc(Review::getCreateTime));
    }

    /** 查询某订单下的全部评价 */
    @Override
    public List<Review> getReviewsByOrder(Long orderId) {
        return list(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId));
    }

    /** 计算用户平均评分，无评价时返回 null */
    @Override
    public Double getAverageRating(Long userId) {
        List<Review> reviews = getReviewsForUser(userId);
        if (reviews.isEmpty()) return null;
        return reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
    }

    /** 被评价者对评价发起申诉，仅允许一次且状态置为申诉中 */
    @Override
    @Transactional
    public void appealReview(Long userId, Long reviewId, String reason) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (!review.getTargetId().equals(userId)) {
            throw new BusinessException(403, "只有被评价者才能申诉");
        }
        if (review.getAppealStatus() != null && review.getAppealStatus() != 0) {
            throw new BusinessException("已提交过申诉，请等待处理");
        }
        review.setAppealReason(reason);
        review.setAppealStatus(1);
        review.setAppealTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    /** 被评价者回复评价，每条评价仅可回复一次 */
    @Override
    @Transactional
    public void replyReview(Long userId, Long reviewId, String reply) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        if (!review.getTargetId().equals(userId)) {
            throw new BusinessException(403, "只有被评价者才能回复");
        }
        if (review.getReply() != null && !review.getReply().isEmpty()) {
            throw new BusinessException("已回复过该评价");
        }
        review.setReply(reply);
        review.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    /** 分页查询我收到的评价（仅正常状态） */
    @Override
    public Page<Review> getMyReceivedReviews(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getTargetId, userId)
                .eq(Review::getStatus, 1)
                .orderByDesc(Review::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /** 分页查询我发出的评价 */
    @Override
    public Page<Review> getMyGivenReviews(Long userId, int page, int size) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getReviewerId, userId)
                .orderByDesc(Review::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }
}
