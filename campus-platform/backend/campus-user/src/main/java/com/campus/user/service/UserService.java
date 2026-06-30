package com.campus.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.user.dto.*;
import com.campus.common.dto.UserVO;
import com.campus.common.entity.User;

public interface UserService extends IService<User> {

    LoginVO login(LoginReq req);

    void register(RegisterReq req);

    UserVO getCurrentUser(Long userId);

    User getByUsername(String username);

    void logout();

    /** 修改个人信息 */
    UserVO updateProfile(Long userId, UpdateProfileReq req);

    /** 修改密码 */
    void changePassword(Long userId, ChangePasswordReq req);

    /** 密码找回 - 发送验证码 */
    void sendResetCode(String email);

    /** 密码找回 - 重置密码 */
    void resetPassword(ResetPasswordReq req);

    /** 账号注销 */
    void deleteAccount(Long userId);

    /** 引导设置管理员（仅当系统无管理员时可用） */
    void bootstrapAdmin(String username, String password);
}
