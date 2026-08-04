package com.yangaobo.feign;
import com.yangaobo.entity.Course;
import com.yangaobo.fallback.CourseFeignFallback;
import com.yangaobo.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** @author 杨奥博 */
@FeignClient(value = "ml-course", fallback = CourseFeignFallback.class)
public interface CourseFeign {

    @GetMapping("/api/v1/course/select/{id}")
    Result<Course> select(@PathVariable("id") Long id);
}