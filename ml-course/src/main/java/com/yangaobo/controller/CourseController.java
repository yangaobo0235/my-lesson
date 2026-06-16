package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.CourseInsertDTO;
import com.yangaobo.dto.CoursePageDTO;
import com.yangaobo.dto.CourseSimpleListVO;
import com.yangaobo.dto.CourseUpdateDTO;
import com.yangaobo.es.CourseDoc;
import com.yangaobo.result.Result;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Course;
import com.yangaobo.service.CourseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 课程表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "课程表接口")
@RequestMapping("/api/v1/course")
public class CourseController {
    @Resource
    private CourseService courseService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条课程记录")
    @PostMapping("insert")
    @com.yangaobo.security.RequireAdmin
    public boolean insert(@Validated @RequestBody CourseInsertDTO dto) {
        return courseService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条课程记录")
    @GetMapping("select/{id}")
    public Course select(@PathVariable("id") Long id) {
        return courseService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部课程记录，仅返回简单信息")
    @GetMapping("simpleList")
    public List<CourseSimpleListVO> simpleList() {
        return courseService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询课程记录")
    @GetMapping("page")
    public PageVO<Course> page(@Validated CoursePageDTO dto) {
        return courseService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条课程记录")
    @PutMapping("update")
    @com.yangaobo.security.RequireAdmin
    public boolean update(@Validated @RequestBody CourseUpdateDTO dto) {
        return courseService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条课程记录")
    @DeleteMapping("delete/{id}")
    @com.yangaobo.security.RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return courseService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除课程记录")
    @DeleteMapping("deleteBatch")
    @com.yangaobo.security.RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return courseService.deleteBatch(ids);
    }

    @Operation(summary = "修改 - 课程封面", description = "按主键修改课程的封面图片")
    @PostMapping("/uploadCover/{id}")
    @com.yangaobo.security.RequireAdmin
    public Result<String> uploadCover(@RequestParam("coverFile") MultipartFile coverFile, @PathVariable("id") Long id) {
        return new Result<>(courseService.uploadCover(coverFile, id));
    }

    @Operation(summary = "修改 - 课程摘要", description = "按主键修改课程的摘要图片")
    @PostMapping("/uploadSummary/{id}")
    @com.yangaobo.security.RequireAdmin
    public Result<String> uploadSummary(@RequestParam("summaryFile") MultipartFile summaryFile, @PathVariable("id") Long id) {
        return new Result<>(courseService.uploadSummary(summaryFile, id));
    }

    @Operation(summary = "搜索 - 课程列表", description = "按课程名称或作者名称分页搜索课程信息")
    @GetMapping("search")
    public PageVO<CourseDoc> search(@Validated CoursePageDTO dto) {
        return courseService.search(dto);
    }

}
