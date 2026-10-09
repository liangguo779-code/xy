package com.campus.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.common.entity.Address;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址表 Mapper。常规 CRUD 由 MyBatis-Plus BaseMapper 提供，无自定义 SQL。
 */
@Mapper
public interface AddressMapper extends BaseMapper<Address> {
}
