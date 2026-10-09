package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.campus.common.entity.SysConfig;
import com.campus.common.result.R;
import com.campus.common.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 管理后台-系统参数配置：查询与维护系统配置项 */
@RestController
@RequestMapping("/api/admin/config")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SysConfigService sysConfigService;

    /** 查询全部系统配置 */
    @GetMapping
    public R<List<SysConfig>> list() {
        return R.ok(sysConfigService.listAll());
    }

    /** 新增或更新单条系统配置（key 不存在则创建） */
    @PutMapping
    public R<Void> set(@RequestBody SetConfigReq req) {
        sysConfigService.setValue(req.key, req.value, req.description);
        return R.ok();
    }

    /** 修改系统配置请求 */
    public static class SetConfigReq {
        /** 配置键 */
        public String key;
        /** 配置值 */
        public String value;
        /** 配置项说明 */
        public String description;
    }
}
