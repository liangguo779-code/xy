package com.campus.user.service;

import java.util.Map;

public interface CaptchaService {

    /**
     * 生成数学计算验证码
     * @return {captchaId, imageBase64} captchaId 用于后续校验，imageBase64 为验证码图片
     */
    Map<String, String> generate();

    /**
     * 校验验证码答案
     * @param captchaId 验证码 ID
     * @param answer 用户输入的答案
     * @return true=验证通过
     */
    boolean verify(String captchaId, String answer);
}
