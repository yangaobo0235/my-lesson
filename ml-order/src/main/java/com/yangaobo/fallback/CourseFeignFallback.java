package com.yangaobo.fallback;

import com.yangaobo.entity.Course;
import com.yangaobo.feign.CourseFeign;
import com.yangaobo.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author 杨奥博
 */
@Slf4j
@Component
public class CourseFeignFallback implements CourseFeign {

    @Override
    public Result<Course> select(Long id) {
        log.error("课程微服务远程调用失败，请联系管理员。");
        return null;
    }
}