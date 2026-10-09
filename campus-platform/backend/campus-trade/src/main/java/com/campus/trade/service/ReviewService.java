package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.entity.Review;

import java.util.List;

/** 评价服务，管理订单评价的增删改查、申诉与回复 */
public interface ReviewService extends IService<Review> {

    /** 发表评价 */
    void createReview(Long reviewerId, CreateReviewReq req);

    /** 修改自己的评价 */
    void updateReview(Long reviewerId, Long reviewId, CreateReviewReq req);

    /** 删除自己的评价 */
    void deleteReview(Long reviewerId, Long reviewId);

    /** 查询某用户收到的全部评价 */
    List<Review> getReviewsForUser(Long targetId);

    /** 查询某订单下的评价 */
    List<Review> getReviewsByOrder(Long orderId);

    /** 计算某用户的平均评分（无评价时返回 null） */
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
