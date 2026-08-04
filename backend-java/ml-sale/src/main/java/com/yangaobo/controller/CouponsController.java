package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.CouponsInsertDTO;
import com.yangaobo.dto.CouponsPageDTO;
import com.yangaobo.dto.CouponsSimpleListVO;
import com.yangaobo.dto.CouponsUpdateDTO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Coupons;
import com.yangaobo.service.CouponsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

/**
 * 优惠卷表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "优惠卷表接口")
@RequestMapping("/api/v1/coupons")
public class CouponsController {
    @Resource
    private CouponsService couponsService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条优惠卷记录")
    @PostMapping("insert")
    @com.yangaobo.security.RequireAdmin
    public boolean insert(@Validated @RequestBody CouponsInsertDTO dto) {
        return couponsService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条优惠卷记录")
    @GetMapping("select/{id}")
    public Coupons select(@PathVariable("id") Long id) {
        return couponsService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部优惠卷记录，仅返回简单信息")
    @GetMapping("simpleList")
    public List<CouponsSimpleListVO> simpleList() {
        return couponsService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询优惠卷记录")
    @GetMapping("page")
    public PageVO<Coupons> page(@Validated CouponsPageDTO dto) {
        return couponsService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条优惠卷记录")
    @PutMapping("update")
    @com.yangaobo.security.RequireAdmin
    public boolean update(@Validated @RequestBody CouponsUpdateDTO dto) {
        return couponsService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条优惠卷记录")
    @DeleteMapping("delete/{id}")
    @com.yangaobo.security.RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return couponsService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除优惠卷记录")
    @DeleteMapping("deleteBatch")
    @com.yangaobo.security.RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return couponsService.deleteBatch(ids);
    }

    @Operation(summary = "查询 - 兑换口令", description = "按兑换口令查询一条优惠卷记录")
    @GetMapping("selectByCode/{code}")
    public Coupons selectByCode(@PathVariable("code") String code) {
        return couponsService.selectByCode(code);
    }

}
