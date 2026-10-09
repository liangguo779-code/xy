package com.campus.app.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.result.R;
import com.campus.common.entity.Address;
import com.campus.common.entity.User;
import com.campus.app.dto.AddressVO;
import com.campus.app.dto.UserSimpleVO;
import com.campus.app.dto.UserStatsVO;
import com.campus.common.dto.UserVO;
import com.campus.common.mapper.AddressMapper;
import com.campus.common.mapper.UserMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 内部用户信息查询接口（用户/收货地址查询 + 统计），保留给未来服务拆分使用。
 * <p>当前无调用方：全库无 Feign，前端与各业务模块均未请求 /internal/**；其中统计数据当前由 AdminController 直接聚合。
 */
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AddressMapper addressMapper;

    /** 根据用户ID查询用户信息 */
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

    /** 批量查询用户信息 */
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

    /** 获取用户昵称，昵称为空时回退到用户名 */
    @GetMapping("/{id}/nickname")
    public R<String> getNickname(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return R.ok("未知用户");
        }
        return R.ok(user.getNickname() != null ? user.getNickname() : user.getUsername());
    }

    /** 获取用户简要信息（昵称、头像、角色） */
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

    /** 获取用户默认收货地址，无默认地址时返回 null 数据 */
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

    /** 根据地址ID查询收货地址，校验地址归属该用户 */
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

    /** 获取用户统计数据 */
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
