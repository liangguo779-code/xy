package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.R;
import com.campus.trade.dto.AppealReviewReq;
import com.campus.trade.dto.CreateReviewReq;
import com.campus.trade.dto.ReplyReviewReq;
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

    @GetMapping("/user/{userId}")
    public R<List<Review>> getUserReviews(@PathVariable Long userId) {
        return R.ok(reviewService.getReviewsForUser(userId));
    }

    @GetMapping("/me")
    public R<List<Review>> myReviews() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reviewService.getReviewsForUser(userId));
    }

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

    @GetMapping("/me/received")
    public R<Page<Review>> myReceivedReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reviewService.getMyReceivedReviews(userId, null, page, size));
    }

    @GetMapping("/me/given")
    public R<Page<Review>> myGivenReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reviewService.getMyGivenReviews(userId, page, size));
    }

    @GetMapping("/order/{orderId}")
    public R<List<Review>> getOrderReviews(@PathVariable Long orderId) {
        return R.ok(reviewService.getReviewsByOrder(orderId));
    }

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

    @PostMapping("/{id}/appeal")
    public R<Void> appealReview(@PathVariable Long id, @Valid @RequestBody AppealReviewReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.appealReview(userId, id, req.getReason());
        return R.ok();
    }

    @PostMapping("/{id}/reply")
    public R<Void> replyReview(@PathVariable Long id, @Valid @RequestBody ReplyReviewReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.replyReview(userId, id, req.getReply());
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> updateReview(@PathVariable Long id, @Valid @RequestBody CreateReviewReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.updateReview(userId, id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteReview(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.deleteReview(userId, id);
        return R.ok();
    }
}
