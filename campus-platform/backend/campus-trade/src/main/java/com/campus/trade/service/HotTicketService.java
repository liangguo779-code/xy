package com.campus.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.trade.dto.HotTicketMessage;
import com.campus.trade.dto.HotTicketVO;
import com.campus.trade.entity.Goods;
import com.campus.trade.entity.Order;
import com.campus.trade.enums.OrderStatus;
import com.campus.trade.mapper.GoodsMapper;
import com.campus.trade.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 热卖票据查询服务：订单在 worker 消费前只以 Redis 消息形态存在，
 * 票据详情/我的票据/管理端监控都从三个桶（pending / confirmed / processing）读取。
 *
 * <p>合并展示约定：前端「我的订单」= DB 订单 + 本服务的票据列表（票据排前），
 * 票据无 DB id，点击按 {@code orderNo} 跳 {@code /hot-ticket/:orderNo}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotTicketService {

    private final HotSaleStockService stockService;
    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;

    // ── 消息定位 ──────────────────────────────────────────────────────────────

    /**
     * 在指定商品的待确认桶中按 orderNo 定位消息（确认/取消的主路径）。
     * goodsId 未知时回退扫全部热卖商品桶（兜底）。
     */
    public HotTicketMessage findPendingMessage(String orderNo, Long goodsId) {
        if (goodsId != null) {
            HotTicketMessage msg = findInList(stockService.listPending(goodsId), orderNo);
            return msg;
        }
        for (Goods goods : listHotGoods()) {
            HotTicketMessage msg = findInList(stockService.listPending(goods.getId()), orderNo);
            if (msg != null) {
                return msg;
            }
        }
        return null;
    }

    /**
     * 在三个桶里找消息（票据详情用）：待确认 → 已确认 → 处理中。
     * 全部落空说明消息已消费完（DB 应有订单）或已释放。
     */
    public HotTicketMessage findMessage(String orderNo) {
        for (Goods goods : listHotGoods()) {
            HotTicketMessage msg = findInList(stockService.listPending(goods.getId()), orderNo);
            if (msg != null) {
                return msg;
            }
        }
        HotTicketMessage msg = findInList(stockService.listConfirmed(), orderNo);
        if (msg != null) {
            return msg;
        }
        return findInList(stockService.listProcessing(), orderNo);
    }

    /** 扫全部热卖商品的待确认消息（超时扫描用） */
    public List<HotTicketMessage> listAllPending() {
        List<HotTicketMessage> result = new ArrayList<>();
        for (Goods goods : listHotGoods()) {
            for (String member : stockService.listPending(goods.getId())) {
                HotTicketMessage msg = stockService.parseMember(member);
                if (msg != null) {
                    result.add(msg);
                }
            }
        }
        return result;
    }

    /** 已确认队列全部消息（管理端监控） */
    public List<HotTicketMessage> listConfirmed() {
        return parseAll(stockService.listConfirmed());
    }

    /** 处理中转区全部消息（管理端监控/对账） */
    public List<HotTicketMessage> listProcessing() {
        return parseAll(stockService.listProcessing());
    }

    // ── VO 组装 ───────────────────────────────────────────────────────────────

    /** 票据消息 → VO（phase 决定 status 码与剩余秒数） */
    public HotTicketVO toVO(HotTicketMessage msg, String phase) {
        HotTicketVO vo = new HotTicketVO();
        vo.setOrderNo(msg.getOrderNo());
        vo.setGoodsId(msg.getGoodsId());
        vo.setGoodsTitle(msg.getGoodsTitle());
        vo.setGoodsImage(msg.getGoodsImage());
        vo.setQty(msg.getQty());
        vo.setGoodsAmount(msg.getGoodsAmount());
        vo.setServiceFee(msg.getServiceFee());
        vo.setDeliveryFeePayer(msg.getDeliveryFeePayer());
        vo.setBuyerId(msg.getBuyerId());
        vo.setBuyerNickname(msg.getBuyerNickname());
        vo.setAddress(msg.getAddress());
        vo.setPhase(phase);
        vo.setCreatedAt(msg.getCreatedAt());
        vo.setExpireAt(msg.getExpireAt());

        if (HotTicketVO.PHASE_PENDING_CONFIRM.equals(phase)) {
            vo.setPhaseDesc("待确认");
            vo.setStatus(OrderStatus.HOT_PENDING_CONFIRM.getCode());
            long remain = msg.getExpireAt() == null ? 0
                    : Math.max(0, (msg.getExpireAt() - System.currentTimeMillis()) / 1000);
            vo.setRemainSeconds(remain);
        } else if (HotTicketVO.PHASE_PROCESSING.equals(phase)) {
            vo.setPhaseDesc("处理中");
            vo.setStatus(OrderStatus.HOT_PROCESSING.getCode());
            vo.setRemainSeconds(0L);
        } else {
            vo.setPhaseDesc("已下单");
            vo.setStatus(OrderStatus.PENDING_DELIVERY.getCode());
            vo.setRemainSeconds(0L);
            Order order = findOrderByNo(msg.getOrderNo());
            if (order != null) {
                vo.setOrderId(order.getId());
            }
        }
        return vo;
    }

    /** 已落库订单 → 票据 DONE 态 VO（票据消失后详情页回查用） */
    public HotTicketVO toDoneVO(Order order) {
        HotTicketVO vo = new HotTicketVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setGoodsId(order.getGoodsId());
        vo.setOrderId(order.getId());
        vo.setQty(order.getQuantity() == null ? 1 : order.getQuantity());
        vo.setGoodsAmount(order.getGoodsAmount());
        vo.setServiceFee(order.getServiceFee());
        vo.setDeliveryFeePayer(order.getDeliveryFeePayer());
        vo.setBuyerId(order.getBuyerId());
        vo.setAddress(order.getPickupLocation());
        vo.setPhase(HotTicketVO.PHASE_DONE);
        vo.setPhaseDesc("已下单");
        vo.setStatus(OrderStatus.PENDING_DELIVERY.getCode());
        vo.setRemainSeconds(0L);
        vo.setExpireAt(order.getExpireTime() == null ? null : order.getExpireTime().toEpochSecond(
                java.time.ZoneId.systemDefault().getRules().getOffset(java.time.LocalDateTime.now())) * 1000);
        vo.setCreatedAt(order.getCreateTime() == null ? null : order.getCreateTime().toEpochSecond(
                java.time.ZoneId.systemDefault().getRules().getOffset(java.time.LocalDateTime.now())) * 1000);

        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods != null) {
            vo.setGoodsTitle(goods.getTitle());
            vo.setGoodsImage(firstImage(goods.getImages()));
        }
        return vo;
    }

    // ── 对外查询（controller 用） ─────────────────────────────────────────────

    /**
     * 票据详情：先找 Redis 消息，落空则回查 DB 订单（已消费完 → DONE）。
     * 非本人票据拒访（orderNo 高熵但不外泄他人数据）。
     */
    public HotTicketVO getTicket(String orderNo, Long currentUserId) {
        HotTicketMessage msg = findMessage(orderNo);
        if (msg != null) {
            if (!msg.getBuyerId().equals(currentUserId)) {
                throw new BusinessException(403, "无权查看该票据");
            }
            String phase = resolvePhase(msg, orderNo);
            return toVO(msg, phase);
        }
        Order order = findOrderByNo(orderNo);
        if (order == null) {
            throw new BusinessException("票据不存在或已过期");
        }
        if (!order.getBuyerId().equals(currentUserId)) {
            throw new BusinessException(403, "无权查看该票据");
        }
        return toDoneVO(order);
    }

    /** 我的票据：三个桶中属于该买家的全部消息（票据排前，供「我的订单」合并展示） */
    public List<HotTicketVO> myTickets(Long buyerId) {
        List<HotTicketVO> result = new ArrayList<>();
        for (HotTicketMessage msg : listAllPending()) {
            if (msg.getBuyerId() != null && msg.getBuyerId().equals(buyerId)) {
                result.add(toVO(msg, HotTicketVO.PHASE_PENDING_CONFIRM));
            }
        }
        for (HotTicketMessage msg : listConfirmed()) {
            if (msg.getBuyerId() != null && msg.getBuyerId().equals(buyerId)) {
                result.add(toVO(msg, HotTicketVO.PHASE_PROCESSING));
            }
        }
        for (HotTicketMessage msg : listProcessing()) {
            if (msg.getBuyerId() != null && msg.getBuyerId().equals(buyerId)) {
                result.add(toVO(msg, HotTicketVO.PHASE_PROCESSING));
            }
        }
        return result;
    }

    /** 管理端全量票据（订单监控合并展示） */
    public List<HotTicketVO> adminTickets() {
        List<HotTicketVO> result = new ArrayList<>();
        for (HotTicketMessage msg : listAllPending()) {
            result.add(toVO(msg, HotTicketVO.PHASE_PENDING_CONFIRM));
        }
        for (HotTicketMessage msg : listConfirmed()) {
            result.add(toVO(msg, HotTicketVO.PHASE_PROCESSING));
        }
        for (HotTicketMessage msg : listProcessing()) {
            result.add(toVO(msg, HotTicketVO.PHASE_PROCESSING));
        }
        return result;
    }

    // ── private ───────────────────────────────────────────────────────────────

    /** 消息所在桶决定阶段：pending 桶 / 全局两桶分开判 */
    private String resolvePhase(HotTicketMessage msg, String orderNo) {
        for (Goods goods : listHotGoods()) {
            if (findInList(stockService.listPending(goods.getId()), orderNo) != null) {
                return HotTicketVO.PHASE_PENDING_CONFIRM;
            }
        }
        return HotTicketVO.PHASE_PROCESSING;
    }

    private Order findOrderByNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo).last("LIMIT 1"));
    }

    private List<Goods> listHotGoods() {
        return goodsMapper.selectList(new LambdaQueryWrapper<Goods>().eq(Goods::getGoodsType, 1));
    }

    private HotTicketMessage findInList(List<String> members, String orderNo) {
        for (String member : members) {
            HotTicketMessage msg = stockService.parseMember(member);
            if (msg != null && orderNo.equals(msg.getOrderNo())) {
                return msg;
            }
        }
        return null;
    }

    private List<HotTicketMessage> parseAll(List<String> members) {
        List<HotTicketMessage> result = new ArrayList<>();
        for (String member : members) {
            HotTicketMessage msg = stockService.parseMember(member);
            if (msg != null) {
                result.add(msg);
            }
        }
        return result;
    }

    private String firstImage(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return null;
        }
        try {
            List<String> images = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(imagesJson, List.class);
            return images.isEmpty() ? null : images.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
