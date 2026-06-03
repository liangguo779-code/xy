package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.user.dto.AddressReq;
import com.campus.user.entity.Address;
import com.campus.user.mapper.AddressMapper;
import com.campus.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    @Override
    public List<Address> getMyAddresses(Long userId) {
        return list(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getUpdateTime));
    }

    @Override
    public Address getAddress(Long userId, Long addressId) {
        Address address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在");
        }
        return address;
    }

    @Override
    @Transactional
    public Address createAddress(Long userId, AddressReq req) {
        // 如果设为默认，先取消其他默认
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            clearDefault(userId);
        }

        Address address = new Address();
        address.setUserId(userId);
        address.setContactName(req.getContactName());
        address.setPhone(req.getPhone());
        address.setBuilding(req.getBuilding());
        address.setDetail(req.getDetail());
        address.setIsDefault(req.getIsDefault() != null ? req.getIsDefault() : 0);

        // 如果是第一个地址，自动设为默认
        Long count = count(new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId));
        if (count == 0) {
            address.setIsDefault(1);
        }

        save(address);
        return address;
    }

    @Override
    @Transactional
    public Address updateAddress(Long userId, AddressReq req) {
        if (req.getId() == null) {
            throw new BusinessException("地址ID不能为空");
        }
        Address existing = getAddress(userId, req.getId());

        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            clearDefault(userId);
        }

        existing.setContactName(req.getContactName());
        existing.setPhone(req.getPhone());
        existing.setBuilding(req.getBuilding());
        existing.setDetail(req.getDetail());
        if (req.getIsDefault() != null) {
            existing.setIsDefault(req.getIsDefault());
        }

        updateById(existing);
        return existing;
    }

    @Override
    public void deleteAddress(Long userId, Long addressId) {
        Address address = getAddress(userId, addressId);
        removeById(addressId);

        // 如果删除的是默认地址，把第一个设为默认
        if (address.getIsDefault() == 1) {
            Address first = getOne(new LambdaQueryWrapper<Address>()
                    .eq(Address::getUserId, userId)
                    .orderByDesc(Address::getCreateTime)
                    .last("LIMIT 1"));
            if (first != null) {
                first.setIsDefault(1);
                updateById(first);
            }
        }
    }

    @Override
    public Address getDefaultAddress(Long userId) {
        return getOne(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1));
    }

    private void clearDefault(Long userId) {
        update(new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1)
                .set(Address::getIsDefault, 0));
    }
}
