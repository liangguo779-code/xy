package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.DeliveryTrack;
import org.apache.ibatis.annotations.Mapper;

/** 配送轨迹表 Mapper，提供基础 CRUD */
@Mapper
public interface DeliveryTrackMapper extends BaseMapper<DeliveryTrack> {
}
