package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.exception.BusinessException;
import com.campus.user.dto.AddressReq;
import com.campus.common.entity.Address;
import com.campus.common.mapper.AddressMapper;
import com.campus.user.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 收货地址服务实现，维护默认地址唯一性（同一用户至多一条默认地址） */
@Service
@RequiredArgsConstructor
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    /** 查询用户全部地址，默认地址与最近更新的排在前面 */
    @Override
    public List<Address> getMyAddresses(Long userId) {
        return list(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getUpdateTime));
    }

    /** 按 ID 查询地址并校验归属，不存在或非本人地址统一按"地址不存在"处理，避免泄露他人地址 */
    @Override
    public Address getAddress(Long userId, Long addressId) {
        Address address = getById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException("地址不存在");
        }
        return address;
    }

    /** 新增地址；事务保证"清默认 + 落库"原子，避免出现多条默认地址 */
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

    /** 修改地址，按 req.id 定位并校验归属；事务边界同新增，保证默认地址唯一 */
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

    /** 删除地址；若删的是默认地址则自动补位，保证用户始终有默认地址可用 */
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

    /** 查询用户默认地址（isDefault=1），没有默认地址时返回 null */
    @Override
    public Address getDefaultAddress(Long userId) {
        return getOne(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1));
    }

    /** 将该用户所有默认地址标记清零，供设置新默认前调用 */
    private void clearDefault(Long userId) {
        update(new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1)
                .set(Address::getIsDefault, 0));
    }
}
