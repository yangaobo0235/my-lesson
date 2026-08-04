package com.yangaobo.controller;

import com.yangaobo.dto.SeasonInsertDTO;
import com.yangaobo.dto.SeasonPageDTO;
import com.yangaobo.vo.SeasonSimpleListVO;
import com.yangaobo.dto.SeasonUpdateDTO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.yangaobo.entity.Season;
import com.yangaobo.service.SeasonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;

/**
 * 季次表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "季次表接口")
@RequestMapping("/api/v1/season")
public class SeasonController {
    @Resource
    private SeasonService seasonService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条季次记录")
    @PostMapping("insert")
    @com.yangaobo.security.RequireAdmin
    public boolean insert(@Validated @RequestBody SeasonInsertDTO dto) {
        return seasonService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条季次记录")
    @GetMapping("select/{id}")
    public Season select(@PathVariable("id") Long id) {
        return seasonService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部季次记录，仅返回简单信息")
    @GetMapping("simpleList")
    public List<SeasonSimpleListVO> simpleList() {
        return seasonService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询季次记录")
    @GetMapping("page")
    public PageVO<Season> page(@Validated SeasonPageDTO dto) {
        return seasonService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条季次记录")
    @PutMapping("update")
    @com.yangaobo.security.RequireAdmin
    public boolean update(@Validated @RequestBody SeasonUpdateDTO dto) {
        return seasonService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条季次记录")
    @DeleteMapping("delete/{id}")
    @com.yangaobo.security.RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return seasonService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除季次记录")
    @DeleteMapping("deleteBatch")
    @com.yangaobo.security.RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return seasonService.deleteBatch(ids);
    }

}
