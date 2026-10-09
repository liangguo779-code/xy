package com.campus.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.user.dto.*;
import com.campus.common.dto.UserVO;
import com.campus.common.entity.User;

/** 用户服务，负责登录注册、资料维护、密码找回与管理员引导 */
public interface UserService extends IService<User> {

    /** 账号密码登录，校验凭据与账号状态后签发令牌；ip/userAgent 用于防爆破计数与登录审计 */
    LoginVO login(LoginReq req, String ip, String userAgent);

    /** 邮箱注册（username 取邮箱），需通过邮箱验证码校验 */
    void register(RegisterReq req);

    /** 获取指定用户的资料视图 */
    UserVO getCurrentUser(Long userId);

    /** 按用户名（即邮箱）查询用户，不存在返回 null */
    User getByUsername(String username);

    /** 退出登录，销毁当前会话 */
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
