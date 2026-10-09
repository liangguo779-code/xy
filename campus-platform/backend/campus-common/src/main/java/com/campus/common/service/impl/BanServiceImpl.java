package com.campus.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.BanRecord;
import com.campus.common.mapper.BanRecordMapper;
import com.campus.common.service.BanService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 封禁服务实现。封禁采用"删旧建新"维护生效记录，判定生效需同时满足
 * status=1 且未过 banUntil，避免历史记录干扰。
 */
@Service
public class BanServiceImpl extends ServiceImpl<BanRecordMapper, BanRecord> implements BanService {

    @Override
    public void banUser(Long userId, String banType, String reason, int days, Long operatorId) {
        // 先解除同类型旧封禁
        remove(new LambdaQueryWrapper<BanRecord>()
                .eq(BanRecord::getTargetType, "user")
                .eq(BanRecord::getTargetValue, String.valueOf(userId))
                .eq(BanRecord::getBanType, banType)
                .eq(BanRecord::getStatus, 1));

        BanRecord record = new BanRecord();
        record.setTargetType("user");
        // 统一存字符串，与 IP 封禁共用 targetValue 字段
        record.setTargetValue(String.valueOf(userId));
        record.setBanType(banType);
        record.setReason(reason);
        record.setBanUntil(LocalDateTime.now().plusDays(days));
        record.setOperatorId(operatorId);
        record.setStatus(1);
        save(record);
    }

    @Override
    public void banIp(String ip, String banType, String reason, int days, Long operatorId) {
        remove(new LambdaQueryWrapper<BanRecord>()
                .eq(BanRecord::getTargetType, "ip")
                .eq(BanRecord::getTargetValue, ip)
                .eq(BanRecord::getBanType, banType)
                .eq(BanRecord::getStatus, 1));

        BanRecord record = new BanRecord();
        record.setTargetType("ip");
        record.setTargetValue(ip);
        record.setBanType(banType);
        record.setReason(reason);
        record.setBanUntil(LocalDateTime.now().plusDays(days));
        record.setOperatorId(operatorId);
        record.setStatus(1);
        save(record);
    }

    /** 解除封禁仅置状态为 0，保留记录便于审计追溯 */
    @Override
    public void unban(Long recordId, Long operatorId) {
        update(new LambdaUpdateWrapper<BanRecord>()
                .eq(BanRecord::getId, recordId)
                .set(BanRecord::getStatus, 0));
    }

    /** 命中"指定能力"或 all 级封禁均视为被封；LIMIT 1 防多条异常 */
    @Override
    public BanRecord checkUserBan(Long userId, String banType) {
        return getOne(new LambdaQueryWrapper<BanRecord>()
                .eq(BanRecord::getTargetType, "user")
                .eq(BanRecord::getTargetValue, String.valueOf(userId))
                .eq(BanRecord::getStatus, 1)
                .and(w -> w.eq(BanRecord::getBanType, banType).or().eq(BanRecord::getBanType, "all"))
                .ge(BanRecord::getBanUntil, LocalDateTime.now())
                .last("LIMIT 1"));
    }

    @Override
    public BanRecord checkIpBan(String ip, String banType) {
        return getOne(new LambdaQueryWrapper<BanRecord>()
                .eq(BanRecord::getTargetType, "ip")
                .eq(BanRecord::getTargetValue, ip)
                .eq(BanRecord::getStatus, 1)
                .and(w -> w.eq(BanRecord::getBanType, banType).or().eq(BanRecord::getBanType, "all"))
                .ge(BanRecord::getBanUntil, LocalDateTime.now())
                .last("LIMIT 1"));
    }

    @Override
    public Page<BanRecord> listBanRecords(String targetType, Integer status, int page, int size) {
        LambdaQueryWrapper<BanRecord> wrapper = new LambdaQueryWrapper<>();
        if (targetType != null) wrapper.eq(BanRecord::getTargetType, targetType);
        if (status != null) wrapper.eq(BanRecord::getStatus, status);
        wrapper.orderByDesc(BanRecord::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    /** 批量把已过期但仍标为生效的记录置为已解除，由定时任务触发 */
    @Override
    public void cleanExpiredBans() {
        update(new LambdaUpdateWrapper<BanRecord>()
                .eq(BanRecord::getStatus, 1)
                .lt(BanRecord::getBanUntil, LocalDateTime.now())
                .set(BanRecord::getStatus, 0));
    }
}
