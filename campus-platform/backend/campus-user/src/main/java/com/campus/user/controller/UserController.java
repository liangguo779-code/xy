package com.campus.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.user.dto.*;
import com.campus.common.dto.UserVO;
import com.campus.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取指定用户公开信息（卖家主页等场景） */
    @GetMapping("/{id}")
    public R<UserVO> getUserById(@PathVariable Long id) {
        return R.ok(userService.getCurrentUser(id));
    }

    /** 修改个人信息 */
    @PutMapping("/profile")
    public R<UserVO> updateProfile(@RequestBody UpdateProfileReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.updateProfile(userId, req));
    }

    /** 修改密码 */
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        userService.changePassword(userId, req);
        return R.ok();
    }

    /** 注销账号 */
    @DeleteMapping("/account")
    public R<Void> deleteAccount() {
        Long userId = StpUtil.getLoginIdAsLong();
        userService.deleteAccount(userId);
        return R.ok();
    }
}
