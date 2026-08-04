package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.SeckillDetailInsertDTO;
import com.yangaobo.dto.SeckillDetailPageDTO;
import com.yangaobo.dto.SeckillDetailUpdateDTO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.SeckillDetail;
import com.yangaobo.service.SeckillDetailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

/**
 * 秒杀明细表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "秒杀明细表接口")
@RequestMapping("/api/v1/seckillDetail")
public class SeckillDetailController {

    @Resource
    private SeckillDetailService seckillDetailService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条秒杀明细记录")
    @PostMapping("insert")
    @com.yangaobo.security.RequireAdmin
    public boolean insert(@Validated @RequestBody SeckillDetailInsertDTO dto) {
        return seckillDetailService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条秒杀明细记录")
    @GetMapping("select/{id}")
    public SeckillDetail select(@PathVariable("id") Long id) {
        return seckillDetailService.select(id);
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询秒杀明细记录")
    @GetMapping("page")
    public PageVO<SeckillDetail> page(@Validated SeckillDetailPageDTO dto) {
        return seckillDetailService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条秒杀明细记录")
    @PutMapping("update")
    @com.yangaobo.security.RequireAdmin
    public boolean update(@Validated @RequestBody SeckillDetailUpdateDTO dto) {
        return seckillDetailService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条秒杀明细记录")
    @DeleteMapping("delete/{id}")
    @com.yangaobo.security.RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return seckillDetailService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除秒杀明细记录")
    @DeleteMapping("deleteBatch")
    @com.yangaobo.security.RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return seckillDetailService.deleteBatch(ids);
    }


}
