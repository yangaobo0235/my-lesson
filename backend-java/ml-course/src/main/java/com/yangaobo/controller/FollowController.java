package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.FollowInsertDTO;
import com.yangaobo.dto.FollowPageDTO;
import com.yangaobo.dto.FollowUpdateDTO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Follow;
import com.yangaobo.service.FollowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

/**
 * 收藏表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "收藏表接口")
@RequestMapping("/api/v1/follow")
public class FollowController {
    @Resource
    private FollowService followService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条收藏记录")
    @PostMapping("insert")
    public boolean insert(@Validated @RequestBody FollowInsertDTO dto) {
        return followService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条收藏记录")
    @GetMapping("select/{id}")
    public Follow select(@PathVariable("id") Long id) {
        return followService.select(id);
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询收藏记录")
    @GetMapping("page")
    public PageVO<Follow> page(@Validated FollowPageDTO dto) {
        return followService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条收藏记录")
    @PutMapping("update")
    public boolean update(@Validated @RequestBody FollowUpdateDTO dto) {
        return followService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条收藏记录")
    @DeleteMapping("delete/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return followService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除收藏记录")
    @DeleteMapping("deleteBatch")
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return followService.deleteBatch(ids);
    }

}
