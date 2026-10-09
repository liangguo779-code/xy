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

/** 评价接口：查询/申诉/回复/修改/删除交易评价 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 获取指定用户收到的评价列表 */
    @GetMapping("/user/{userId}")
    public R<List<Review>> getUserReviews(@PathVariable Long userId) {
        return R.ok(reviewService.getReviewsForUser(userId));
    }

    /** 获取我收到的评价列表 */
    @GetMapping("/me")
    public R<List<Review>> myReviews() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reviewService.getReviewsForUser(userId));
    }

    /** 获取我的平均评分与评价总数 */
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

    /** 分页获取我收到的评价 */
    @GetMapping("/me/received")
    public R<Page<Review>> myReceivedReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reviewService.getMyReceivedReviews(userId, null, page, size));
    }

    /** 分页获取我发出的评价 */
    @GetMapping("/me/given")
    public R<Page<Review>> myGivenReviews(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(reviewService.getMyGivenReviews(userId, page, size));
    }

    /** 获取某笔订单下的评价（买卖双方各一条） */
    @GetMapping("/order/{orderId}")
    public R<List<Review>> getOrderReviews(@PathVariable Long orderId) {
        return R.ok(reviewService.getReviewsByOrder(orderId));
    }

    /** 获取用户评价统计：平均分、评价数、好评率（4-5 星占比，返回整数百分比） */
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

    /** 对评价发起申诉（仅被评方可申诉自己的差评） */
    @PostMapping("/{id}/appeal")
    public R<Void> appealReview(@PathVariable Long id, @Valid @RequestBody AppealReviewReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.appealReview(userId, id, req.getReason());
        return R.ok();
    }

    /** 对评价进行公开回复 */
    @PostMapping("/{id}/reply")
    public R<Void> replyReview(@PathVariable Long id, @Valid @RequestBody ReplyReviewReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.replyReview(userId, id, req.getReply());
        return R.ok();
    }

    /** 修改自己发布的评价 */
    @PutMapping("/{id}")
    public R<Void> updateReview(@PathVariable Long id, @Valid @RequestBody CreateReviewReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.updateReview(userId, id, req);
        return R.ok();
    }

    /** 删除自己发布的评价 */
    @DeleteMapping("/{id}")
    public R<Void> deleteReview(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        reviewService.deleteReview(userId, id);
        return R.ok();
    }
}
