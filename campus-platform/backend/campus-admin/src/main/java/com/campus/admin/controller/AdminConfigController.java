package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.campus.app.entity.SysConfig;
import com.campus.common.result.R;
import com.campus.app.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@SaCheckRole("ADMIN")
@RequiredArgsConstructor
public class AdminConfigController {

    private final SysConfigService sysConfigService;

    @GetMapping
    public R<List<SysConfig>> list() {
        return R.ok(sysConfigService.listAll());
    }

    @PutMapping
    public R<Void> set(@RequestBody SetConfigReq req) {
        sysConfigService.setValue(req.key, req.value, req.description);
        return R.ok();
    }

    public static class SetConfigReq {
        public String key;
        public String value;
        public String description;
    }
}
