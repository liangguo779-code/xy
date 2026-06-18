package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.trade.dto.DeliveryOrderVO;
import com.campus.trade.entity.DeliveryTrack;
import com.campus.trade.service.DeliveryService;
import com.campus.trade.service.DeliveryTrackService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final DeliveryTrackService deliveryTrackService;
    private final RedissonClient redisson;

    @GetMapping("/pending")
    public R<List<DeliveryOrderVO>> pendingOrders() {
        return R.ok(deliveryService.getPendingOrders());
    }

    @PutMapping("/{id}/accept")
    public R<DeliveryOrderVO> acceptOrder(@PathVariable Long id,
                                           @RequestParam(required = false) BigDecimal lat,
                                           @RequestParam(required = false) BigDecimal lng) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        DeliveryOrderVO vo = deliveryService.acceptOrder(runnerId, id, lat, lng);
        return R.ok(vo);
    }

    @PutMapping("/{id}/pickup")
    public R<DeliveryOrderVO> pickupGoods(
            @PathVariable Long id,
            @RequestParam(required = false) String photoUrl,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        DeliveryOrderVO vo = deliveryService.pickupGoods(runnerId, id, photoUrl, lat, lng);
        return R.ok(vo);
    }

    @PutMapping("/{id}/deliver")
    public R<DeliveryOrderVO> deliverGoods(
            @PathVariable Long id,
            @RequestParam(required = false) String photoUrl,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        DeliveryOrderVO vo = deliveryService.deliverGoods(runnerId, id, photoUrl, lat, lng);
        return R.ok(vo);
    }

    @GetMapping("/my")
    public R<List<DeliveryOrderVO>> myDeliveries() {
        Long runnerId = StpUtil.getLoginIdAsLong();
        return R.ok(deliveryService.getMyDeliveries(runnerId));
    }

    /** 获取物流轨迹 */
    @GetMapping("/{id}/tracks")
    public R<List<DeliveryTrack>> getTracks(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(deliveryTrackService.getTracks(id, userId));
    }

    /** 交付员上报位置（限制每10秒一次） */
    @PostMapping("/{id}/location")
    public R<Void> reportLocation(
            @PathVariable Long id,
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam(required = false) String address) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        // 频率限制：每10秒只能上报一次（原子操作）
        String rateLimitKey = "delivery:location:" + id + ":" + runnerId;
        RBucket<String> bucket = redisson.getBucket(rateLimitKey);
        if (!bucket.setIfAbsent("1", java.time.Duration.ofSeconds(10))) {
            throw new BusinessException("上报过于频繁，请10秒后再试");
        }
        deliveryTrackService.addTrack(id, runnerId, "location", lat, lng, address, null);
        return R.ok();
    }
}
