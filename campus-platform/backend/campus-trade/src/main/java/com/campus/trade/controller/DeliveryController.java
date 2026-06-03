package com.campus.trade.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.trade.dto.DeliveryOrderVO;
import com.campus.trade.entity.DeliveryConfig;
import com.campus.trade.entity.DeliveryTrack;
import com.campus.trade.service.DeliveryFeeService;
import com.campus.trade.service.DeliveryService;
import com.campus.trade.service.DeliveryTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final DeliveryFeeService deliveryFeeService;
    private final DeliveryTrackService deliveryTrackService;

    @GetMapping("/pending")
    public R<List<DeliveryOrderVO>> pendingOrders() {
        return R.ok(deliveryService.getPendingOrders());
    }

    @PutMapping("/{id}/accept")
    public R<DeliveryOrderVO> acceptOrder(@PathVariable Long id,
                                           @RequestParam(required = false) BigDecimal lat,
                                           @RequestParam(required = false) BigDecimal lng) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        DeliveryOrderVO vo = deliveryService.acceptOrder(runnerId, id);
        deliveryTrackService.addTrack(id, runnerId, "accept", lat, lng, null, null);
        return R.ok(vo);
    }

    @PutMapping("/{id}/pickup")
    public R<DeliveryOrderVO> pickupGoods(
            @PathVariable Long id,
            @RequestParam(required = false) String photoUrl,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        DeliveryOrderVO vo = deliveryService.pickupGoods(runnerId, id, photoUrl);
        deliveryTrackService.addTrack(id, runnerId, "pickup", lat, lng, null, photoUrl);
        return R.ok(vo);
    }

    @PutMapping("/{id}/deliver")
    public R<DeliveryOrderVO> deliverGoods(
            @PathVariable Long id,
            @RequestParam(required = false) String photoUrl,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        DeliveryOrderVO vo = deliveryService.deliverGoods(runnerId, id, photoUrl);
        deliveryTrackService.addTrack(id, runnerId, "deliver", lat, lng, null, photoUrl);
        return R.ok(vo);
    }

    @GetMapping("/my")
    public R<List<DeliveryOrderVO>> myDeliveries() {
        Long runnerId = StpUtil.getLoginIdAsLong();
        return R.ok(deliveryService.getMyDeliveries(runnerId));
    }

    /** 计算配送服务费 */
    @GetMapping("/fee")
    public R<Map<String, Object>> calculateFee(
            @RequestParam(defaultValue = "1") int floor,
            @RequestParam(defaultValue = "true") boolean hasElevator) {
        BigDecimal fee = deliveryFeeService.calculateFee(floor, hasElevator);
        return R.ok(Map.of("fee", fee));
    }

    /** 获取配送费配置 */
    @GetMapping("/config")
    public R<DeliveryConfig> getConfig() {
        return R.ok(deliveryFeeService.getConfig());
    }

    /** 获取物流轨迹 */
    @GetMapping("/{id}/tracks")
    public R<List<DeliveryTrack>> getTracks(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(deliveryTrackService.getTracks(id, userId));
    }

    /** 交付员上报位置 */
    @PostMapping("/{id}/location")
    public R<Void> reportLocation(
            @PathVariable Long id,
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam(required = false) String address) {
        Long runnerId = StpUtil.getLoginIdAsLong();
        deliveryTrackService.addTrack(id, runnerId, "location", lat, lng, address, null);
        return R.ok();
    }
}
