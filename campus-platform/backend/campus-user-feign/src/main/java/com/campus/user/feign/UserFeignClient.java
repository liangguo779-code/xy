package com.campus.user.feign;

import com.campus.common.result.R;
import com.campus.user.feign.dto.AddressVO;
import com.campus.user.feign.dto.UserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "campus-user-service", path = "/internal/user")
public interface UserFeignClient {

    @GetMapping("/{id}")
    R<UserVO> getUserById(@PathVariable("id") Long id);

    @PostMapping("/batch")
    R<List<UserVO>> getUsersByIds(@RequestBody List<Long> ids);

    @GetMapping("/{id}/nickname")
    R<String> getNickname(@PathVariable("id") Long id);

    @GetMapping("/{id}/simple")
    R<Map<String, Object>> getUserSimple(@PathVariable("id") Long id);

    @GetMapping("/address/{userId}/default")
    R<AddressVO> getDefaultAddress(@PathVariable("userId") Long userId);

    @GetMapping("/address/{userId}/{addressId}")
    R<AddressVO> getAddress(@PathVariable("userId") Long userId, @PathVariable("addressId") Long addressId);

    @GetMapping("/stats")
    R<Map<String, Object>> getUserStats();
}
