package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.entity.Review;

import java.util.List;

public interface ReviewService extends IService<Review> {

    void createReview(Long reviewerId, CreateReviewReq req);

    void updateReview(Long reviewerId, Long reviewId, CreateReviewReq req);

    void deleteReview(Long reviewerId, Long reviewId);

    List<Review> getReviewsForUser(Long targetId);

    List<Review> getReviewsByOrder(Long orderId);

    Double getAverageRating(Long userId);

    /** 申诉评价 */
    void appealReview(Long userId, Long reviewId, String reason);

    /** 回复评价 */
    void replyReview(Long userId, Long reviewId, String reply);

    /** 我收到的评价（分页） */
    Page<Review> getMyReceivedReviews(Long userId, Integer status, int page, int size);

    /** 我发出的评价（分页） */
    Page<Review> getMyGivenReviews(Long userId, int page, int size);
}
