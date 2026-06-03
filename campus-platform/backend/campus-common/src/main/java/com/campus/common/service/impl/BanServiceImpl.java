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

    @Override
    public void unban(Long recordId, Long operatorId) {
        update(new LambdaUpdateWrapper<BanRecord>()
                .eq(BanRecord::getId, recordId)
                .set(BanRecord::getStatus, 0));
    }

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

    @Override
    public void cleanExpiredBans() {
        update(new LambdaUpdateWrapper<BanRecord>()
                .eq(BanRecord::getStatus, 1)
                .lt(BanRecord::getBanUntil, LocalDateTime.now())
                .set(BanRecord::getStatus, 0));
    }
}
