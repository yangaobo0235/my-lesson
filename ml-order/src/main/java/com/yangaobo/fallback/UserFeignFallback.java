package com.yangaobo.fallback;

import com.yangaobo.entity.User;
import com.yangaobo.feign.UserFeign;
import com.yangaobo.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author 杨奥博
 */
@Slf4j
@Component
public class UserFeignFallback implements UserFeign {

    @Override
    public Result<User> select(Long id) {
        log.error("用户微服务远程调用失败，请联系管理员。");
        return null;
    }
}
