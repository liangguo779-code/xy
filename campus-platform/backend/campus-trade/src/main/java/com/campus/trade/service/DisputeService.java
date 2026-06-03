package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Dispute;

public interface DisputeService extends IService<Dispute> {
    void createDispute(Long reporterId, Long orderId, String reason, String evidenceImages);
    Page<Dispute> listMyDisputes(Long userId, Integer status, int page, int size);
    Page<Dispute> listDisputes(Integer status, int page, int size);
    void resolveDispute(Long disputeId, Long handlerId, String result, int status);
}
