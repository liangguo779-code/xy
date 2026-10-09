package com.campus.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.user.dto.AddressReq;
import com.campus.common.entity.Address;

import java.util.List;

/** 收货地址服务，管理当前用户的地址增删改查与默认地址维护 */
public interface AddressService extends IService<Address> {

    /** 查询指定用户的全部收货地址（默认地址优先、最近更新优先） */
    List<Address> getMyAddresses(Long userId);

    /**
     * 查询指定地址详情并校验归属
     * @param userId 当前用户 ID
     * @param addressId 地址 ID
     * @return 归属校验通过的地址
     */
    Address getAddress(Long userId, Long addressId);

    /** 新增收货地址，首条地址自动成为默认地址 */
    Address createAddress(Long userId, AddressReq req);

    /** 修改收货地址（按 req.id 定位，校验归属） */
    Address updateAddress(Long userId, AddressReq req);

    /** 删除收货地址，若删除的是默认地址则自动提升其余地址中的最新一条为默认 */
    void deleteAddress(Long userId, Long addressId);

    /** 查询指定用户的默认收货地址，无默认地址时返回 null */
    Address getDefaultAddress(Long userId);
}
