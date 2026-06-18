package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.entity.Review;

import java.util.List;

public interface ReviewService extends IService<Review> {

    /**
     * 创建评价
     */
    void createReview(Long reviewerId, CreateReviewReq req);

    /**
     * 编辑评价（24小时内可修改）
     */
    void updateReview(Long reviewerId, Long reviewId, CreateReviewReq req);

    /**
     * 删除评价（24小时内可删除）
     */
    void deleteReview(Long reviewerId, Long reviewId);

    /**
     * 获取某用户收到的评价
     */
    List<Review> getReviewsForUser(Long targetId);

    /**
     * 获取某订单的评价
     */
    List<Review> getReviewsByOrder(Long orderId);

    /**
     * 获取用户平均评分
     */
    Double getAverageRating(Long userId);
}
