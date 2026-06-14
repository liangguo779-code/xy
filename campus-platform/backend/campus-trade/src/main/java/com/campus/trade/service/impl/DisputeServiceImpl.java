package com.campus.trade.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.trade.entity.Dispute;
import com.campus.trade.entity.Order;
import com.campus.trade.mapper.DisputeMapper;
import com.campus.trade.mapper.OrderMapper;
import com.campus.trade.service.DisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DisputeServiceImpl extends ServiceImpl<DisputeMapper, Dispute> implements DisputeService {

    private final OrderMapper orderMapper;

    @Override
    public void createDispute(Long reporterId, Long orderId, String reason, String evidenceImages) {
        // 校验申请人是否为该订单的买家或卖家
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getBuyerId().equals(reporterId) && !order.getSellerId().equals(reporterId)) {
            throw new BusinessException(403, "无权对此订单发起纠纷");
        }
        // 防止重复提交：检查是否已有待处理的纠纷
        Long existing = count(new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getOrderId, orderId)
                .eq(Dispute::getReporterId, reporterId)
                .eq(Dispute::getStatus, 0));
        if (existing > 0) {
            throw new BusinessException("您已对该订单提交过纠纷，请等待处理结果");
        }

        Dispute dispute = new Dispute();
        dispute.setOrderId(orderId);
        dispute.setReporterId(reporterId);
        dispute.setReason(reason);
        dispute.setEvidenceImages(evidenceImages);
        dispute.setStatus(0);
        save(dispute);
    }

    @Override
    public Page<Dispute> listMyDisputes(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<Dispute> wrapper = new LambdaQueryWrapper<Dispute>()
                .eq(Dispute::getReporterId, userId);
        if (status != null) wrapper.eq(Dispute::getStatus, status);
        wrapper.orderByDesc(Dispute::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Page<Dispute> listDisputes(Integer status, int page, int size) {
        LambdaQueryWrapper<Dispute> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Dispute::getStatus, status);
        wrapper.orderByDesc(Dispute::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public void resolveDispute(Long disputeId, Long handlerId, String result, int status) {
        Dispute dispute = getById(disputeId);
        if (dispute == null) throw new BusinessException("纠纷不存在");
        if (dispute.getStatus() == 2 || dispute.getStatus() == 3) {
            throw new BusinessException("该纠纷已处理完毕");
        }
        // 校验 status 只能为 2（已解决）或 3（已驳回）
        if (status != 2 && status != 3) {
            throw new BusinessException("无效的处理状态，只能为 2（已解决）或 3（已驳回）");
        }
        dispute.setHandlerId(handlerId);
        dispute.setResult(result);
        dispute.setStatus(status);
        updateById(dispute);
    }
}
