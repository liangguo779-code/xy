package com.campus.common.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.common.entity.SysConfig;

import java.util.List;

/**
 * 系统配置服务。提供 key-value 配置的读写能力，适合存储可动态调整的运行参数。
 */
public interface SysConfigService extends IService<SysConfig> {

    /**
     * 读取配置值
     *
     * @param key          配置键
     * @param defaultValue 键不存在时返回的默认值
     */
    String getValue(String key, String defaultValue);

    /** 写入配置值：键不存在则新增，存在则更新（description 传 null 时保留原说明） */
    void setValue(String key, String value, String description);

    /** 查询全部配置项（管理端列表用） */
    List<SysConfig> listAll();
}
