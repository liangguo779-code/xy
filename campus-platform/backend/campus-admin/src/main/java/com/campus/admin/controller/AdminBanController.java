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

/** 管理后台-封禁管理：对用户/IP 执行封禁与解封，并查询封禁记录与生效状态 */
@RestController
@RequestMapping("/api/admin/bans")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminBanController {

    private final BanService banService;
    private final UserMapper userMapper;

    /** 封禁用户（禁止封禁管理员，也禁止封禁自己） */
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

    /** 封禁 IP */
    @PostMapping("/ip")
    public R<Void> banIp(@Valid @RequestBody BanIpReq req) {
        banService.banIp(req.ip, req.banType, req.reason, req.days, StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    /** 解除封禁 */
    @PutMapping("/{id}/unban")
    public R<Void> unban(@PathVariable Long id) {
        banService.unban(id, StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    /**
     * 分页查询封禁记录
     * @param targetType 封禁对象类型（user/ip），不传则查全部
     * @param status 记录状态，不传则查全部
     */
    @GetMapping
    public R<Page<BanRecord>> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(banService.listBanRecords(targetType, status, page, size));
    }

    /** 查询用户在全部/交易/消息/论坛四个业务域的封禁状态 */
    @GetMapping("/check/user/{userId}")
    public R<Map<String, BanRecord>> checkUserBan(@PathVariable Long userId) {
        Map<String, BanRecord> result = new java.util.HashMap<>();
        result.put("all", banService.checkUserBan(userId, "all"));
        result.put("trade", banService.checkUserBan(userId, "trade"));
        result.put("message", banService.checkUserBan(userId, "message"));
        result.put("forum", banService.checkUserBan(userId, "forum"));
        return R.ok(result);
    }

    /** 封禁用户请求 */
    @Data
    public static class BanUserReq {
        /** 目标用户 ID */
        @NotNull(message = "用户ID不能为空")
        private Long userId;
        /** 封禁范围：all-全部 trade-交易 message-消息 forum-论坛 */
        @NotBlank(message = "封禁类型不能为空")
        private String banType;
        /** 封禁原因 */
        @NotBlank(message = "原因不能为空")
        private String reason;
        /** 封禁天数，最小 1 天 */
        @Min(1)
        private int days = 7;
    }

    /** 封禁 IP 请求 */
    @Data
    public static class BanIpReq {
        /** 目标 IP */
        @NotBlank(message = "IP不能为空")
        private String ip;
        /** 封禁范围：all-全部 trade-交易 message-消息 forum-论坛 */
        @NotBlank(message = "封禁类型不能为空")
        private String banType;
        /** 封禁原因 */
        @NotBlank(message = "原因不能为空")
        private String reason;
        /** 封禁天数，最小 1 天 */
        @Min(1)
        private int days = 7;
    }
}
