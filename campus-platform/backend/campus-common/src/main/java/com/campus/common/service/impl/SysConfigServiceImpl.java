package com.campus.common.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.entity.SysConfig;
import com.campus.common.mapper.SysConfigMapper;
import com.campus.common.service.SysConfigService;
import org.springframework.stereotype.Service;

/**
 * 系统配置服务实现。config_key 视为唯一键，读写均按键定位。
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    /** 键存在返回库中值，否则返回 defaultValue（不落库） */
    @Override
    public String getValue(String key, String defaultValue) {
        SysConfig config = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : defaultValue;
    }

    /** 按键 upsert：无则插入新配置，有则覆盖 value 并按需刷新说明 */
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
