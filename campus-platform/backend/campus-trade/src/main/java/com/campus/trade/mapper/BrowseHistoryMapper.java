package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.BrowseHistory;
import org.apache.ibatis.annotations.Mapper;

/** 商品浏览历史表 Mapper，提供基础 CRUD */
@Mapper
public interface BrowseHistoryMapper extends BaseMapper<BrowseHistory> {
}
