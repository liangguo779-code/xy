package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.entity.Review;
import com.campus.trade.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // 创建评价接口统一使用 POST /api/orders/review

    /**
     * 获取某用户收到的评价
     */
    @GetMapping("/user/{userId}")
    public R<List<Review>> getUserReviews(@PathVariable Long userId) {
        return R.ok(reviewService.getReviewsForUser(userId));
    }

    /**
     * 获取当前用户收到的评价
     */
    @GetMapping("/me")
    public R<List<Review>> myReviews() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reviewService.getReviewsForUser(userId));
    }

    /**
     * 获取当前用户的平均评分
     */
    @GetMapping("/me/rating")
    public R<Map<String, Object>> myRating() {
        Long userId = StpUtil.getLoginIdAsLong();
        Double avg = reviewService.getAverageRating(userId);
        List<Review> reviews = reviewService.getReviewsForUser(userId);
        return R.ok(Map.of(
                "averageRating", avg != null ? avg : 0,
                "reviewCount", reviews.size()
        ));
    }

    /**
     * 获取某订单的评价
     */
    @GetMapping("/order/{orderId}")
    public R<List<Review>> getOrderReviews(@PathVariable Long orderId) {
        return R.ok(reviewService.getReviewsByOrder(orderId));
    }

    /**
     * 获取某用户的评价统计
     */
    @GetMapping("/user/{userId}/stats")
    public R<Map<String, Object>> getUserStats(@PathVariable Long userId) {
        Double avg = reviewService.getAverageRating(userId);
        List<Review> reviews = reviewService.getReviewsForUser(userId);
        long positiveCount = reviews.stream().filter(r -> r.getRating() >= 4).count();
        double positiveRate = reviews.isEmpty() ? 0 : (double) positiveCount / reviews.size() * 100;
        return R.ok(Map.of(
                "averageRating", avg != null ? avg : 0,
                "reviewCount", reviews.size(),
                "positiveRate", Math.round(positiveRate)
        ));
    }
}
