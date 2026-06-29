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

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public R<List<Address>> list() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.getMyAddresses(userId));
    }

    @GetMapping("/{id}")
    public R<Address> detail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.getAddress(userId, id));
    }

    @GetMapping("/default")
    public R<Address> getDefault() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.getDefaultAddress(userId));
    }

    @PostMapping
    public R<Address> create(@Valid @RequestBody AddressReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.createAddress(userId, req));
    }

    @PutMapping
    public R<Address> update(@Valid @RequestBody AddressReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(addressService.updateAddress(userId, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        addressService.deleteAddress(userId, id);
        return R.ok();
    }
}
