package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.DeliveryOrder;
import org.apache.ibatis.annotations.Mapper;

/** 配送单表 Mapper，提供基础 CRUD */
@Mapper
public interface DeliveryOrderMapper extends BaseMapper<DeliveryOrder> {
}
