package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.entity.BanRecord;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.common.service.BanService;
import com.campus.common.entity.User;
import com.campus.common.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/bans")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminBanController {

    private final BanService banService;
    private final UserMapper userMapper;

    @PostMapping("/user")
    public R<Void> banUser(@Valid @RequestBody BanUserReq req) {
        // 不能封禁管理员
        User user = userMapper.selectById(req.userId);
        if (user != null && Integer.valueOf(1).equals(user.getRole())) {
            throw new BusinessException("不能封禁管理员用户");
        }
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (req.userId.equals(currentUserId)) {
            throw new BusinessException("不能封禁自己");
        }
        banService.banUser(req.userId, req.banType, req.reason, req.days, currentUserId);
        return R.ok();
    }

    @PostMapping("/ip")
    public R<Void> banIp(@Valid @RequestBody BanIpReq req) {
        banService.banIp(req.ip, req.banType, req.reason, req.days, StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @PutMapping("/{id}/unban")
    public R<Void> unban(@PathVariable Long id) {
        banService.unban(id, StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @GetMapping
    public R<Page<BanRecord>> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(banService.listBanRecords(targetType, status, page, size));
    }

    @GetMapping("/check/user/{userId}")
    public R<Map<String, BanRecord>> checkUserBan(@PathVariable Long userId) {
        Map<String, BanRecord> result = new java.util.HashMap<>();
        result.put("all", banService.checkUserBan(userId, "all"));
        result.put("trade", banService.checkUserBan(userId, "trade"));
        result.put("message", banService.checkUserBan(userId, "message"));
        result.put("forum", banService.checkUserBan(userId, "forum"));
        return R.ok(result);
    }

    @Data
    public static class BanUserReq {
        @NotNull(message = "用户ID不能为空")
        private Long userId;
        @NotBlank(message = "封禁类型不能为空")
        private String banType;
        @NotBlank(message = "原因不能为空")
        private String reason;
        @Min(1)
        private int days = 7;
    }

    @Data
    public static class BanIpReq {
        @NotBlank(message = "IP不能为空")
        private String ip;
        @NotBlank(message = "封禁类型不能为空")
        private String banType;
        @NotBlank(message = "原因不能为空")
        private String reason;
        @Min(1)
        private int days = 7;
    }
}
