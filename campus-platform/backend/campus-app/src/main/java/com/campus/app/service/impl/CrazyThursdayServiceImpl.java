package com.campus.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.app.entity.CrazyThursday;
import com.campus.app.entity.CrazyThursdayRegistration;
import com.campus.app.mapper.CrazyThursdayMapper;
import com.campus.app.mapper.CrazyThursdayRegistrationMapper;
import com.campus.app.service.CrazyThursdayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrazyThursdayServiceImpl extends ServiceImpl<CrazyThursdayMapper, CrazyThursday>
        implements CrazyThursdayService {

    private final RedissonClient redisson;
    private final CrazyThursdayMapper thursdayMapper;
    private final CrazyThursdayRegistrationMapper registrationMapper;

    private static final String LOCK_KEY = "crazy_thursday:lock";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_SLOTS = 10;

    @Override
    public Map<String, Object> getStatus(Long userId) {
        Map<String, Object> result = new HashMap<>();

        LocalDateTime now = LocalDateTime.now(ZONE);
        String weekKey = getWeekKey(now);
        LocalDateTime nextThursday = getNextThursday11AM(now);
        long countdown = Math.max(0, nextThursday.atZone(ZONE).toInstant().toEpochMilli()
                - now.atZone(ZONE).toInstant().toEpochMilli());

        boolean isThursday = now.getDayOfWeek() == DayOfWeek.THURSDAY;
        boolean isRegisterTime = isThursday && now.getHour() >= 11 && now.getHour() < 12;
        boolean isAfterDraw = isThursday && now.getHour() >= 12;

        // 获取本周活动
        CrazyThursday activity = getOne(new LambdaQueryWrapper<CrazyThursday>()
                .eq(CrazyThursday::getWeekKey, weekKey));

        // 获取报名列表
        List<CrazyThursdayRegistration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<CrazyThursdayRegistration>()
                        .eq(CrazyThursdayRegistration::getWeekKey, weekKey)
                        .orderByAsc(CrazyThursdayRegistration::getCreateTime));

        boolean alreadyRegistered = userId != null && registrations.stream()
                .anyMatch(r -> r.getUserId().equals(userId));

        // 如果到开奖时间但还没开奖，自动开奖
        if (isAfterDraw && activity != null && activity.getStatus() == 0) {
            drawInternal(weekKey, activity, registrations);
            activity = getOne(new LambdaQueryWrapper<CrazyThursday>()
                    .eq(CrazyThursday::getWeekKey, weekKey));
        }

        // 构建报名用户列表
        List<Map<String, Object>> participantList = registrations.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", r.getUserId());
                    m.put("isWinner", r.getIsWinner());
                    return m;
                })
                .collect(Collectors.toList());

        result.put("countdown", countdown);
        result.put("isRegisterTime", isRegisterTime);
        result.put("alreadyRegistered", alreadyRegistered);
        result.put("weekKey", weekKey);
        result.put("registeredCount", registrations.size());
        result.put("maxSlots", MAX_SLOTS);
        result.put("remainingSlots", Math.max(0, MAX_SLOTS - registrations.size()));
        result.put("participants", participantList);
        result.put("winner", activity != null && activity.getWinnerId() != null
                ? Map.of("userId", activity.getWinnerId(),
                "drawTime", activity.getDrawTime() != null ? activity.getDrawTime().toString() : "")
                : null);
        result.put("status", activity != null ? activity.getStatus() : 0);

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> register(Long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);

        if (now.getDayOfWeek() != DayOfWeek.THURSDAY
                || now.getHour() < 11 || now.getHour() >= 12) {
            throw new RuntimeException("不在报名时间内！每周四 11:00-12:00 开放报名");
        }

        String weekKey = getWeekKey(now);

        boolean alreadyRegistered = registrationMapper.selectCount(
                new LambdaQueryWrapper<CrazyThursdayRegistration>()
                        .eq(CrazyThursdayRegistration::getWeekKey, weekKey)
                        .eq(CrazyThursdayRegistration::getUserId, userId)) > 0;
        if (alreadyRegistered) {
            throw new RuntimeException("你已经报名了！");
        }

        RLock lock = redisson.getLock(LOCK_KEY);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new RuntimeException("系统繁忙，请稍后再试");
            }

            try {
                Long currentCount = registrationMapper.selectCount(
                        new LambdaQueryWrapper<CrazyThursdayRegistration>()
                                .eq(CrazyThursdayRegistration::getWeekKey, weekKey));
                if (currentCount >= MAX_SLOTS) {
                    throw new RuntimeException("本周名额已满（" + MAX_SLOTS + "人）！");
                }

                CrazyThursday activity = getOne(new LambdaQueryWrapper<CrazyThursday>()
                        .eq(CrazyThursday::getWeekKey, weekKey));
                if (activity == null) {
                    activity = new CrazyThursday();
                    activity.setWeekKey(weekKey);
                    activity.setMaxSlots(MAX_SLOTS);
                    activity.setStatus(0);
                    thursdayMapper.insert(activity);
                }

                CrazyThursdayRegistration reg = new CrazyThursdayRegistration();
                reg.setWeekKey(weekKey);
                reg.setUserId(userId);
                reg.setIsWinner(0);
                registrationMapper.insert(reg);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "报名成功！周四12点系统自动开奖");
                result.put("position", currentCount + 1);
                result.put("remainingSlots", MAX_SLOTS - currentCount - 1);
                return result;

            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("系统异常");
        }
    }

    @Override
    public Map<String, Object> draw() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        String weekKey = getWeekKey(now);

        CrazyThursday activity = getOne(new LambdaQueryWrapper<CrazyThursday>()
                .eq(CrazyThursday::getWeekKey, weekKey));
        if (activity == null || activity.getStatus() == 1) {
            throw new RuntimeException("本周活动不存在或已开奖");
        }

        List<CrazyThursdayRegistration> registrations = registrationMapper.selectList(
                new LambdaQueryWrapper<CrazyThursdayRegistration>()
                        .eq(CrazyThursdayRegistration::getWeekKey, weekKey));

        return drawInternal(weekKey, activity, registrations);
    }

    private Map<String, Object> drawInternal(String weekKey, CrazyThursday activity,
                                              List<CrazyThursdayRegistration> registrations) {
        Map<String, Object> result = new HashMap<>();

        if (registrations.isEmpty()) {
            activity.setStatus(1);
            activity.setDrawTime(LocalDateTime.now(ZONE));
            thursdayMapper.updateById(activity);
            result.put("success", false);
            result.put("message", "本周无人报名，奖品累积到下周！");
            return result;
        }

        Random random = new Random();
        int winnerIndex = random.nextInt(registrations.size());
        CrazyThursdayRegistration winner = registrations.get(winnerIndex);

        winner.setIsWinner(1);
        registrationMapper.updateById(winner);

        activity.setWinnerId(winner.getUserId());
        activity.setStatus(1);
        activity.setDrawTime(LocalDateTime.now(ZONE));
        thursdayMapper.updateById(activity);

        result.put("success", true);
        result.put("message", "开奖完成！中奖者请私信管理员领取奖品");
        result.put("winner", Map.of("userId", winner.getUserId()));
        result.put("totalParticipants", registrations.size());
        result.put("claimTip", "中奖者请私信管理员，发送「疯狂星期四领奖」+ 用户名");

        log.info("疯狂星期四开奖: weekKey={}, winner={}, participants={}",
                weekKey, winner.getUserId(), registrations.size());

        return result;
    }

    private String getWeekKey(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("YYYY")) + "-W"
                + dateTime.format(DateTimeFormatter.ofPattern("ww"));
    }

    private LocalDateTime getNextThursday11AM(LocalDateTime now) {
        LocalDateTime nextThursday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
                .withHour(11).withMinute(0).withSecond(0).withNano(0);
        if (now.getDayOfWeek() == DayOfWeek.THURSDAY && now.getHour() >= 12) {
            nextThursday = nextThursday.plusWeeks(1);
        }
        return nextThursday;
    }
}
