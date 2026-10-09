package com.campus.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.LoginLog;
import com.campus.common.exception.BusinessException;
import com.campus.user.dto.*;
import com.campus.common.dto.UserVO;
import com.campus.common.entity.User;
import com.campus.common.mapper.LoginLogMapper;
import com.campus.common.mapper.UserMapper;
import com.campus.user.service.CaptchaService;
import com.campus.user.service.EmailService;
import com.campus.user.service.LoginAttemptService;
import com.campus.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/** 用户服务实现，基于 Sa-Token 会话与 Redis 验证码完成认证与账号生命周期管理 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;
    private final LoginLogMapper loginLogMapper;

    /** 密码找回验证码的 Redis key 前缀，完整 key 为前缀 + 邮箱 */
    private static final String RESET_CODE_KEY = "reset_code:";
    /** 注册用邮箱验证码的 Redis key 前缀，与 AuthController 中保持一致 */
    private static final String EMAIL_CODE_KEY = "email_code:";
    /** 验证码错误尝试计数 key 前缀，同一验证码错 5 次即作废 */
    private static final String CODE_ATTEMPTS_KEY = "code_attempts:";
    /** 验证码最大试错次数 */
    private static final int CODE_MAX_ATTEMPTS = 5;

    /** 用户不存在时用于时序对齐的占位 BCrypt 哈希（内容无关，仅消耗同等计算时间） */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    /** 各类验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 5;

    /**
     * 账号密码登录：凭据错误统一返回 401 防止枚举用户名，仅 status=1 可登录。
     * 登录前做防爆破检查（锁定拦截 / 失败达阈值强制图形验证码），全程落审计日志。
     */
    @Override
    public LoginVO login(LoginReq req, String ip, String userAgent) {
        String username = req.getUsername();

        // 1. 防爆破：账号或 IP 被锁直接拒绝
        loginAttemptService.checkNotLocked(username, ip);

        // 2. 连续失败达阈值后强制图形验证码
        if (loginAttemptService.captchaRequired(username)) {
            if (!captchaService.verify(req.getCaptchaId(), req.getCaptchaAnswer())) {
                loginAttemptService.recordFailure(username, ip);
                audit(username, null, ip, userAgent, 0, "图形验证码错误或缺失");
                throw new BusinessException(428, "请完成图形验证码");
            }
        }

        // 3. 凭据校验：用户不存在与密码错误统一 401；不存在时用占位哈希对齐耗时防时序枚举
        User user = getByUsername(username);
        if (user == null) {
            passwordEncoder.matches(req.getPassword(), DUMMY_HASH);
            loginAttemptService.recordFailure(username, ip);
            audit(username, null, ip, userAgent, 0, "用户不存在");
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(username, ip);
            audit(username, user.getId(), ip, userAgent, 0, "密码错误");
            throw new BusinessException(401, "用户名或密码错误");
        }
        // 仅 status=1 可登录；status 为 null 的脏数据同样拒绝（防止拆箱 NPE 与脏数据放行）
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            audit(username, user.getId(), ip, userAgent, 0, "账号已被禁用");
            throw new BusinessException(403, "账号已被禁用");
        }

        // 4. 登录成功：清零防爆破计数；记住我签发 30 天令牌，否则默认 24 小时
        loginAttemptService.clearFailures(username, ip);
        if (Boolean.TRUE.equals(req.getRememberMe())) {
            // 记住我：30 天有效期且不受 2 小时活跃过期约束
            StpUtil.login(user.getId(), new cn.dev33.satoken.stp.SaLoginModel()
                    .setTimeout(30 * 86400).setActiveTimeout(-1));
        } else {
            StpUtil.login(user.getId());
        }
        // 角色映射为 Sa-Token 角色标识：1-ADMIN 2-RUNNER 其余-USER，写入会话供 @SaCheckRole 鉴权
        StpUtil.getSession()
                .set("role", user.getRole() == 1 ? "ADMIN" : user.getRole() == 2 ? "RUNNER" : "USER")
                .set("nickname", user.getNickname());

        audit(username, user.getId(), ip, userAgent, 1, "登录成功");

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUser(toVO(user));
        return vo;
    }

    /** 落登录审计日志；审计失败不影响登录主流程 */
    private void audit(String username, Long userId, String ip, String userAgent, int success, String message) {
        try {
            LoginLog log = new LoginLog();
            log.setUsername(username);
            log.setUserId(userId);
            log.setIp(ip);
            log.setUserAgent(userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
            log.setSuccess(success);
            log.setMessage(message);
            loginLogMapper.insert(log);
        } catch (Exception e) {
            // 审计为旁路，失败仅记录
        }
    }

    /** 邮箱注册：校验邮箱验证码后创建账号（username 取邮箱），成功后销毁验证码 */
    @Override
    public void register(RegisterReq req) {
        String email = req.getEmail();

        // 1. 校验邮箱验证码（限尝试次数 + 常量时间比较）
        verifyCode(EMAIL_CODE_KEY, email, req.getCode());

        // 2. 检查邮箱是否已注册（username = email）
        User existing = getByUsername(email);
        if (existing != null) {
            throw new BusinessException("该邮箱已注册");
        }

        // 2.5 密码不得等于邮箱或其 @ 前缀（防止密码即账号）
        assertPasswordNotIdentity(req.getPassword(), email);

        // 3. 创建用户
        User user = new User();
        user.setUsername(email);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        // 昵称缺省取邮箱 @ 前缀
        user.setNickname(req.getNickname() != null && !req.getNickname().isBlank()
                ? req.getNickname() : email.split("@")[0]);
        user.setRole(0);
        user.setStatus(1);
        save(user);

        // 4. 删除已使用的验证码
        redisTemplate.delete(EMAIL_CODE_KEY + email);
    }

    /**
     * 校验一次性验证码：限尝试次数（错 5 次作废防爆破），常量时间比较防时序侧信道。
     */
    private void verifyCode(String keyPrefix, String email, String code) {
        String cachedCode = redisTemplate.opsForValue().get(keyPrefix + email);
        if (cachedCode == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        // 试错次数上限：达到即作废验证码
        String attemptsKey = CODE_ATTEMPTS_KEY + keyPrefix + email;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts > CODE_MAX_ATTEMPTS) {
            redisTemplate.delete(keyPrefix + email);
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }
        // 常量时间比较，避免按字符比对的时序差异
        boolean matched = MessageDigest.isEqual(
                cachedCode.getBytes(StandardCharsets.UTF_8),
                (code == null ? "" : code).getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            throw new BusinessException("验证码错误");
        }
        redisTemplate.delete(attemptsKey);
    }

    /** 密码不得等于邮箱或邮箱 @ 前缀（大小写不敏感），防止"密码即账号"的弱口令 */
    private void assertPasswordNotIdentity(String password, String email) {
        if (password == null || email == null) {
            return;
        }
        String lower = password.toLowerCase();
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (lower.equals(email.toLowerCase()) || (!local.isBlank() && lower.equals(local.toLowerCase()))) {
            throw new BusinessException("密码不能与邮箱相同");
        }
    }

    /** 获取指定用户资料视图，用户不存在抛 404 业务异常 */
    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toVO(user);
    }

    /** 按用户名（即邮箱）精确查询用户 */
    @Override
    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /** 退出登录，销毁当前会话 */
    @Override
    public void logout() {
        StpUtil.logout();
    }

    /** 修改个人信息，仅覆盖请求中非空字段（支持部分更新） */
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

    /** 修改密码：校验旧密码后加密存储新密码，并登出使旧 token 失效 */
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

        // 修改密码后踢掉该账号全部端的会话（不只当前端），防止被盗 token 继续存活
        StpUtil.logout(userId);
    }

    /** 密码找回：生成 6 位验证码存 Redis 并邮件送达；邮箱不存在时不发信但同样返回成功防枚举 */
    @Override
    public void sendResetCode(String email) {
        // 发送频控：同邮箱 1 分钟 1 条、每日 10 条
        String sentKey = "reset_code:sent:" + email;
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(sentKey, "1", 1, TimeUnit.MINUTES))) {
            throw new BusinessException(429, "发送过于频繁，请1分钟后再试");
        }
        String dailyKey = "reset_code:daily:" + email;
        Long daily = redisTemplate.opsForValue().increment(dailyKey);
        if (daily != null && daily == 1L) {
            redisTemplate.expire(dailyKey, 1, TimeUnit.DAYS);
        }
        if (daily != null && daily > 10) {
            throw new BusinessException(429, "今日发送次数已达上限");
        }

        // 邮箱不存在时静默返回（不报"未注册"），防止枚举有效邮箱
        User user = getByUsername(email);
        if (user == null) {
            return;
        }

        // 生成6位验证码
        String code = RandomUtil.randomNumbers(6);

        // 存入Redis，5分钟过期
        redisTemplate.opsForValue().set(
                RESET_CODE_KEY + email,
                code,
                CODE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 发送邮件
        emailService.sendVerificationCode(email, code);
    }

    /** 重置密码：校验验证码后写入新密码，销毁验证码并踢掉该用户所有会话 */
    @Override
    public void resetPassword(ResetPasswordReq req) {
        String email = req.getEmail();

        // 验证码校验（限尝试次数 + 常量时间比较）
        verifyCode(RESET_CODE_KEY, email, req.getCode());

        // 密码不得等于邮箱或其 @ 前缀
        assertPasswordNotIdentity(req.getNewPassword(), email);

        // 查找用户（username = email）
        User user = getByUsername(email);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        updateById(user);

        // 删除已使用的验证码
        redisTemplate.delete(RESET_CODE_KEY + email);

        // 清除该用户所有会话
        StpUtil.logout(user.getId());
    }

    /** 账号注销（软删除）：禁用账号并清空身份信息，同时释放邮箱占用 */
    @Override
    public void deleteAccount(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 软删除：标记状态为禁用，用户名改为已注销
        user.setStatus(0);
        // username 即邮箱，改名以释放原邮箱，允许其重新注册
        user.setUsername("deleted_" + userId);
        user.setNickname("已注销用户");
        updateById(user);

        // 清除会话
        StpUtil.logout(userId);
    }

    /** 初始化引导管理员：仅当系统中尚无 role=1 管理员时可用，防止接口被滥用提权 */
    @Override
    public void bootstrapAdmin(String username, String password) {
        // Redis 互斥锁收口"检查-创建"竞态：并发请求只允许一个进入初始化
        String lockKey = "admin:bootstrap:lock";
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("初始化正在进行中，请稍后重试");
        }
        try {
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
            audit(username, user.getId(), "bootstrap", "bootstrap-admin", 1, "初始化管理员成功");
        } finally {
            redisTemplate.delete(lockKey);
        }
    }


    /** 实体转视图对象，仅输出 UserVO 中声明的公开字段 */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }
}
