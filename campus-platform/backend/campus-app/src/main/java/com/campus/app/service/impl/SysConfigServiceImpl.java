package com.campus.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.app.entity.SysConfig;
import com.campus.app.mapper.SysConfigMapper;
import com.campus.app.service.SysConfigService;
import org.springframework.stereotype.Service;

@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public String getValue(String key, String defaultValue) {
        SysConfig config = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : defaultValue;
    }

    @Override
    public void setValue(String key, String value, String description) {
        SysConfig config = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            save(config);
        } else {
            config.setConfigValue(value);
            if (description != null) config.setDescription(description);
            updateById(config);
        }
    }

    @Override
    public java.util.List<SysConfig> listAll() {
        return list();
    }
}
