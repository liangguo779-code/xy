package com.campus.common.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 角色权限实现。
 * 为 @SaCheckRole/@SaCheckPermission 等注解提供角色与权限数据源；
 * 当前角色取自登录时写入 session 的 role 字段，权限细粒度未启用（恒为空列表）。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 返回指定用户所拥有的权限列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 项目未启用细粒度权限码，返回空列表表示无 permission 校验
        return new ArrayList<>();
    }

    /**
     * 返回指定用户所拥有的角色列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roles = new ArrayList<>();
        // 从 session 中获取角色
        String role = StpUtil.getSessionByLoginId(loginId).getString("role");
        if (role != null) {
            roles.add(role);
        }
        return roles;
    }
}
