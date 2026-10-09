package com.campus.trade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trade.entity.Dispute;

/** 交易纠纷服务，处理纠纷的发起、查询与平台裁决 */
public interface DisputeService extends IService<Dispute> {
    /** 发起纠纷 */
    void createDispute(Long reporterId, Long orderId, String reason, String evidenceImages);
    /** 分页查询我发起的纠纷，status 为 null 时查全部状态 */
    Page<Dispute> listMyDisputes(Long userId, Integer status, int page, int size);
    /** 分页查询纠纷列表（管理端），status 为 null 时查全部状态 */
    Page<Dispute> listDisputes(Integer status, int page, int size);
    /** 平台裁决纠纷：status 只能为 2-已解决 或 3-已驳回 */
    void resolveDispute(Long disputeId, Long handlerId, String result, int status);
}
