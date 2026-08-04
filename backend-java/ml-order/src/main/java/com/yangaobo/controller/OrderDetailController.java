package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.OrderDetailInsertDTO;
import com.yangaobo.dto.OrderDetailPageDTO;
import com.yangaobo.dto.OrderDetailUpdateDTO;
import com.yangaobo.util.EasyExcelUtil;
import com.yangaobo.vo.OrderDetailSimpleListVO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.OrderDetail;
import com.yangaobo.service.OrderDetailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import com.yangaobo.security.RequireAdmin;

/**
 * 订单明细表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "订单明细表接口")
@RequestMapping("/api/v1/orderDetail")
@RequireAdmin
public class OrderDetailController {

    @Resource
    private OrderDetailService orderDetailService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条订单明细记录")
    @PostMapping("insert")
    public boolean insert(@Validated @RequestBody OrderDetailInsertDTO dto) {
        return orderDetailService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条订单明细记录")
    @GetMapping("select/{id}")
    public OrderDetail select(@PathVariable("id") Long id) {
        return orderDetailService.select(id);
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询订单明细记录")
    @GetMapping("page")
    public PageVO<OrderDetail> page(@Validated OrderDetailPageDTO dto) {
        return orderDetailService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条订单明细记录")
    @PutMapping("update")
    public boolean update(@Validated @RequestBody OrderDetailUpdateDTO dto) {
        return orderDetailService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条订单明细记录")
    @DeleteMapping("delete/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return orderDetailService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除订单明细记录")
    @DeleteMapping("deleteBatch")
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return orderDetailService.deleteBatch(ids);
    }

    @Operation(summary = "查询 - 报表打印", description = "打印订单明细相关的报表数据")
    @GetMapping("excel")
    public void excel(HttpServletResponse response) {
        EasyExcelUtil.download(response, "订单明细统计表", orderDetailService.getExcelData());
    }

}
