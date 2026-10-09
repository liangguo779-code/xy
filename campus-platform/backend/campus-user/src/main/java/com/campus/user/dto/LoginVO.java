package com.campus.user.dto;

import com.campus.common.dto.UserVO;
import lombok.Data;

/** 登录成功返回：登录令牌与用户信息 */
@Data
public class LoginVO {

    /** Sa-Token 登录令牌，后续请求携带用于鉴权 */
    private String token;

    /** 当前登录用户的个人信息 */
    private UserVO user;
}
