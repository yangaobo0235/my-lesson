package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.BannerInsertDTO;
import com.yangaobo.dto.BannerPageDTO;
import com.yangaobo.dto.BannerUpdateDTO;
import com.yangaobo.result.Result;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Banner;
import com.yangaobo.service.BannerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 横幅表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "横幅表接口")
@RequestMapping("/api/v1/banner")
public class BannerController {
    @Resource
    private BannerService bannerService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条横幅记录")
    @PostMapping("insert")
    @com.yangaobo.security.RequireAdmin
    public boolean insert(@Validated @RequestBody BannerInsertDTO dto) {
        return bannerService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条横幅记录")
    @GetMapping("select/{id}")
    public Banner select(@PathVariable("id") Long id) {
        return bannerService.select(id);
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询横幅记录")
    @GetMapping("page")
    public PageVO<Banner> page(@Validated BannerPageDTO dto) {
        return bannerService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条横幅记录")
    @PutMapping("update")
    @com.yangaobo.security.RequireAdmin
    public boolean update(@Validated @RequestBody BannerUpdateDTO dto) {
        return bannerService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条横幅记录")
    @DeleteMapping("delete/{id}")
    @com.yangaobo.security.RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return bannerService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除横幅记录")
    @DeleteMapping("deleteBatch")
    @com.yangaobo.security.RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return bannerService.deleteBatch(ids);
    }

    @Operation(summary = "查询 - 前N条记录", description = "查询前N条横幅记录")
    @GetMapping("top/{n}")
    public List<Banner> top(@PathVariable("n") Long n) {
        return bannerService.top(n);
    }

    @Operation(summary = "修改 - 横幅图片", description = "按主键修改横幅图片")
    @PostMapping("/uploadBanner/{id}")
    @com.yangaobo.security.RequireAdmin
    public Result<String> uploadBanner(@RequestParam("bannerFile") MultipartFile bannerFile, @PathVariable("id") Long id) {
        return new Result<>(bannerService.uploadBanner(bannerFile, id));
    }

}
