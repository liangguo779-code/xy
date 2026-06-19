package com.campus.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.user.entity.Address;
import com.campus.user.entity.User;
import com.campus.feign.user.dto.AddressVO;
import com.campus.feign.user.dto.UserSimpleVO;
import com.campus.feign.user.dto.UserStatsVO;
import com.campus.feign.user.dto.UserVO;
import com.campus.user.mapper.AddressMapper;
import com.campus.user.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 内部调用接口（供其他微服务通过 Feign 调用）
 */
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AddressMapper addressMapper;

    @GetMapping("/{id}")
    public R<UserVO> getUserById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return R.fail("用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return R.ok(vo);
    }

    @PostMapping("/batch")
    public R<List<UserVO>> getUsersByIds(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.ok(List.of());
        }
        List<User> users = userMapper.selectBatchIds(ids);
        List<UserVO> vos = users.stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            return vo;
        }).collect(Collectors.toList());
        return R.ok(vos);
    }

    @GetMapping("/{id}/nickname")
    public R<String> getNickname(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return R.ok("未知用户");
        }
        return R.ok(user.getNickname() != null ? user.getNickname() : user.getUsername());
    }

    @GetMapping("/{id}/simple")
    public R<UserSimpleVO> getUserSimple(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return R.fail("用户不存在");
        }
        UserSimpleVO vo = new UserSimpleVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        return R.ok(vo);
    }

    @GetMapping("/address/{userId}/default")
    public R<AddressVO> getDefaultAddress(@PathVariable Long userId) {
        Address address = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .eq(Address::getIsDefault, 1)
                        .last("LIMIT 1")
        );
        if (address == null) {
            return R.ok(null);
        }
        AddressVO vo = new AddressVO();
        BeanUtils.copyProperties(address, vo);
        return R.ok(vo);
    }

    @GetMapping("/address/{userId}/{addressId}")
    public R<AddressVO> getAddress(@PathVariable Long userId, @PathVariable Long addressId) {
        Address address = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getId, addressId)
                        .eq(Address::getUserId, userId)
        );
        if (address == null) {
            return R.fail("地址不存在");
        }
        AddressVO vo = new AddressVO();
        BeanUtils.copyProperties(address, vo);
        return R.ok(vo);
    }

    @GetMapping("/stats")
    public R<UserStatsVO> getUserStats() {
        long total = userMapper.selectCount(null);
        long active = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        UserStatsVO vo = new UserStatsVO();
        vo.setUserCount(total);
        vo.setActiveCount(active);
        return R.ok(vo);
    }
}
