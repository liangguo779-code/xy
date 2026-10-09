package com.campus.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.common.result.R;
import com.campus.user.dto.AddressReq;
import com.campus.common.entity.Address;
import com.campus.user.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 收货地址管理接口，操作范围限当前登录用户自己的地址 */
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /** 查询当前用户的全部收货地址 */
    @GetMapping
    public R<List<Address>> list() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.getMyAddresses(userId));
    }

    /** 查询指定地址详情（校验归属，非本人地址按不存在处理） */
    @GetMapping("/{id}")
    public R<Address> detail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.getAddress(userId, id));
    }

    /** 查询当前用户的默认收货地址 */
    @GetMapping("/default")
    public R<Address> getDefault() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.getDefaultAddress(userId));
    }

    /** 新增收货地址，首个地址自动设为默认 */
    @PostMapping
    public R<Address> create(@Valid @RequestBody AddressReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.createAddress(userId, req));
    }

    /** 修改收货地址（按 req.id 定位记录） */
    @PutMapping
    public R<Address> update(@Valid @RequestBody AddressReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.updateAddress(userId, req));
    }

    /** 删除收货地址，若删的是默认地址则自动提升最新一条为默认 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        addressService.deleteAddress(userId, id);
        return R.ok();
    }
}
