package com.campus.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.trade.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/** 举报记录表 Mapper，提供基础 CRUD */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
