package com.yangaobo.feign;
import com.yangaobo.entity.User;
import com.yangaobo.fallback.UserFeignFallback;
import com.yangaobo.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** @author 杨奥博 */
@FeignClient(value = "ml-user", fallback = UserFeignFallback.class)
public interface UserFeign {

    @GetMapping("/api/v1/user/select/{id}")
    Result<User> select(@PathVariable("id") Long id);
}
