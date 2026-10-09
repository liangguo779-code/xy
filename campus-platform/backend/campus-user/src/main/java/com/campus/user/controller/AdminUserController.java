package com.campus.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.common.result.R;
import com.campus.common.entity.User;
import com.campus.common.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端用户管理接口：用户检索、禁用/启用、角色调整、数据统计（仅 ADMIN 角色可访问） */
@RestController
@RequestMapping("/api/admin/users")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;

    /** 分页查询用户列表，支持按角色/状态/用户名昵称关键字筛选，按创建时间倒序 */
    @GetMapping
    public R<Page<User>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (role != null) wrapper.eq(User::getRole, role);
        if (status != null) wrapper.eq(User::getStatus, status);
        if (keyword != null) {
            // 关键字同时匹配用户名和昵称
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        return R.ok(userMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /** 启用/禁用账号（status：0-禁用 1-启用），禁止操作自己以免锁死账号；禁用即踢下线 */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException("状态值只能为 0（禁用）或 1（启用）");
        }
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (id.equals(currentUserId)) {
            throw new BusinessException("不能禁用自己的账号");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id).set(User::getStatus, status));
        // 禁用立即踢掉该账号全部端会话，防止已签发 token 继续使用
        if (status == 0) {
            StpUtil.logout(id);
        }
        return R.ok();
    }

    /** 修改用户角色（role：0-普通用户 1-管理员 2-交付员），禁止操作自己；改角色踢下线使新角色即时生效 */
    @PutMapping("/{id}/role")
    public R<Void> updateRole(@PathVariable Long id, @RequestParam Integer role) {
        if (role < 0 || role > 2) {
            throw new BusinessException("角色值只能为 0（普通用户）、1（管理员）、2（交付员）");
        }
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (id.equals(currentUserId)) {
            throw new BusinessException("不能修改自己的角色");
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id).set(User::getRole, role));
        // 角色缓存在登录会话中，踢下线强制重新登录以刷新角色
        StpUtil.logout(id);
        return R.ok();
    }

    /** 用户统计概览：total-总数 active-启用数 runners-交付员数（role=2） */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        long total = userMapper.selectCount(null);
        long active = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        long runners = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, 2));
        return R.ok(Map.of("total", total, "active", active, "runners", runners));
    }
}
