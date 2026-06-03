package com.campus.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.user.dto.*;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    private static final String RESET_CODE_KEY = "reset_code:";
    private static final int CODE_EXPIRE_MINUTES = 5;

    @Override
    public LoginVO login(LoginReq req) {
        User user = getByUsername(req.getUsername());
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }

        StpUtil.login(user.getId());
        StpUtil.getSession()
                .set("role", user.getRole() == 1 ? "ADMIN" : user.getRole() == 2 ? "RUNNER" : "USER")
                .set("nickname", user.getNickname());

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUser(toVO(user));
        return vo;
    }

    @Override
    public void register(RegisterReq req) {
        User existing = getByUsername(req.getUsername());
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setRole(0);
        user.setStatus(1);
        save(user);
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileReq req) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (StringUtils.hasText(req.getNickname())) {
            user.setNickname(req.getNickname());
        }
        if (StringUtils.hasText(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        if (StringUtils.hasText(req.getPhone())) {
            user.setPhone(req.getPhone());
        }
        if (StringUtils.hasText(req.getDormitory())) {
            user.setDormitory(req.getDormitory());
        }

        updateById(user);
        return toVO(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordReq req) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        updateById(user);

        // 修改密码后重新登录，使旧token失效
        StpUtil.logout();
    }

    @Override
    public void sendResetCode(String phone) {
        // 校验手机号是否存在
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            throw new BusinessException("该手机号未注册");
        }

        // 生成6位验证码
        String code = RandomUtil.randomNumbers(6);

        // 存入Redis，5分钟过期
        redisTemplate.opsForValue().set(
                RESET_CODE_KEY + phone,
                code,
                CODE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // TODO: 对接短信服务商发送验证码
        // 目前在日志中输出，方便开发测试
        System.out.println("【验证码】手机号: " + phone + ", 验证码: " + code + " (5分钟内有效)");
    }

    @Override
    public void resetPassword(ResetPasswordReq req) {
        // 验证码校验
        String cachedCode = redisTemplate.opsForValue().get(RESET_CODE_KEY + req.getPhone());
        if (cachedCode == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(req.getCode())) {
            throw new BusinessException("验证码错误");
        }

        // 查找用户
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, req.getPhone()));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        updateById(user);

        // 删除已使用的验证码
        redisTemplate.delete(RESET_CODE_KEY + req.getPhone());

        // 清除该用户所有会话
        StpUtil.logout(user.getId());
    }

    @Override
    public void deleteAccount(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 软删除：标记状态为禁用，用户名改为已注销
        user.setStatus(0);
        user.setUsername("deleted_" + userId);
        user.setNickname("已注销用户");
        updateById(user);

        // 清除会话
        StpUtil.logout(userId);
    }

    @Override
    public void bootstrapAdmin(String username, String password) {
        // 检查是否已有管理员
        Long adminCount = count(new LambdaQueryWrapper<User>().eq(User::getRole, 1));
        if (adminCount > 0) {
            throw new BusinessException("系统已有管理员，无法使用此接口");
        }

        // 查找或创建用户
        User user = getByUsername(username);
        if (user == null) {
            // 创建新用户
            user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setNickname("管理员");
            user.setRole(1);
            user.setStatus(1);
            save(user);
        } else {
            // 提升现有用户为管理员
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(1);
            updateById(user);
        }
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }
}
