package com.campus.common.service;

import java.util.Map;

public interface CrazyThursdayService {

    /**
     * 获取当前状态
     */
    Map<String, Object> getStatus(Long userId);

    /**
     * 报名参与（10个名额）
     */
    Map<String, Object> register(Long userId);

    /**
     * 开奖（系统随机抽取1人）
     */
    Map<String, Object> draw();
}
