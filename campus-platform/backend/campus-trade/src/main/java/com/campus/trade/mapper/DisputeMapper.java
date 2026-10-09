package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Dispute;
import org.apache.ibatis.annotations.Mapper;

/** 交易纠纷表 Mapper，提供基础 CRUD */
@Mapper
public interface DisputeMapper extends BaseMapper<Dispute> {
}
