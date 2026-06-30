package com.campus.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.common.entity.SysConfig;

import java.util.List;

public interface SysConfigService extends IService<SysConfig> {
    String getValue(String key, String defaultValue);
    void setValue(String key, String value, String description);
    List<SysConfig> listAll();
}
