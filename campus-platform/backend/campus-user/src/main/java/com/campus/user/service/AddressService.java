package com.campus.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.user.dto.AddressReq;
import com.campus.common.entity.Address;

import java.util.List;

public interface AddressService extends IService<Address> {

    List<Address> getMyAddresses(Long userId);

    Address getAddress(Long userId, Long addressId);

    Address createAddress(Long userId, AddressReq req);

    Address updateAddress(Long userId, AddressReq req);

    void deleteAddress(Long userId, Long addressId);

    Address getDefaultAddress(Long userId);
}
