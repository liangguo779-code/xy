package com.campus.feign.user;

import com.campus.common.result.R;
import com.campus.feign.user.dto.AddressVO;
import com.campus.feign.user.dto.UserSimpleVO;
import com.campus.feign.user.dto.UserStatsVO;
import com.campus.feign.user.dto.UserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "campus-user-service", path = "/internal/user")
public interface UserFeignClient {

    @GetMapping("/{id}")
    R<UserVO> getUserById(@PathVariable("id") Long id);

    @PostMapping("/batch")
    R<List<UserVO>> getUsersByIds(@RequestBody List<Long> ids);

    @GetMapping("/{id}/nickname")
    R<String> getNickname(@PathVariable("id") Long id);

    @GetMapping("/{id}/simple")
    R<UserSimpleVO> getUserSimple(@PathVariable("id") Long id);

    @GetMapping("/address/{userId}/default")
    R<AddressVO> getDefaultAddress(@PathVariable("userId") Long userId);

    @GetMapping("/address/{userId}/{addressId}")
    R<AddressVO> getAddress(@PathVariable("userId") Long userId, @PathVariable("addressId") Long addressId);

    @GetMapping("/stats")
    R<UserStatsVO> getUserStats();
}
