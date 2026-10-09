package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trade.entity.Review;

/** 管理端评价服务，处理评价申诉与屏蔽/恢复 */
public interface AdminReviewService {

    /** 查看申诉列表 */
    Page<Review> listAppeals(Integer appealStatus, int page, int size);

    /** 处理申诉 */
    void handleAppeal(Long reviewId, Long adminId, boolean approved, String result);

    /** 直接屏蔽/恢复评价 */
    void updateStatus(Long reviewId, int status);

    /** 评价统计 */
    long[] getStats(); // [total, appealed, hidden]
}
