package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.CategoryInsertDTO;
import com.yangaobo.dto.CategoryPageDTO;
import com.yangaobo.dto.CategorySimpleListVO;
import com.yangaobo.dto.CategoryUpdateDTO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Category;
import com.yangaobo.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

/**
 * 课程类别表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "课程类别表接口")
@RequestMapping("/api/v1/category")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条类别记录")
    @PostMapping("insert")
    @com.yangaobo.security.RequireAdmin
    public boolean insert(@Validated @RequestBody CategoryInsertDTO dto) {
        return categoryService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条类别记录")
    @GetMapping("select/{id}")
    public Category select(@PathVariable("id") Long id) {
        return categoryService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部类别记录，仅返回简单信息")
    @GetMapping("simpleList")
    public List<CategorySimpleListVO> simpleList() {
        return categoryService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询类别记录")
    @GetMapping("page")
    public PageVO<Category> page(@Validated CategoryPageDTO dto) {
        return categoryService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条类别记录")
    @PutMapping("update")
    @com.yangaobo.security.RequireAdmin
    public boolean update(@Validated @RequestBody CategoryUpdateDTO dto) {
        return categoryService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条类别记录")
    @DeleteMapping("delete/{id}")
    @com.yangaobo.security.RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return categoryService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除类别记录")
    @DeleteMapping("deleteBatch")
    @com.yangaobo.security.RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return categoryService.deleteBatch(ids);
    }

}
