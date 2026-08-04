package com.yangaobo.controller;

import com.yangaobo.dto.EpisodeInsertDTO;
import com.yangaobo.dto.EpisodePageDTO;
import com.yangaobo.vo.EpisodeSimpleListVO;
import com.yangaobo.dto.EpisodeUpdateDTO;
import com.yangaobo.es.BarrageDoc;
import com.yangaobo.result.Result;
import com.yangaobo.util.EasyExcelUtil;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.yangaobo.entity.Episode;
import com.yangaobo.service.EpisodeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 集次表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "集次表接口")
@RequestMapping("/api/v1/episode")
public class EpisodeController {
    @Resource
    private EpisodeService episodeService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条集次记录")
    @PostMapping("insert")
    @com.yangaobo.security.RequireAdmin
    public boolean insert(@Validated @RequestBody EpisodeInsertDTO dto) {
        return episodeService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条集次记录")
    @GetMapping("select/{id}")
    public Episode select(@PathVariable("id") Long id) {
        return episodeService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部集次记录，仅返回简单信息")
    @GetMapping("simpleList")
    public List<EpisodeSimpleListVO> simpleList() {
        return episodeService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询集次记录")
    @GetMapping("page")
    public PageVO<Episode> page(@Validated EpisodePageDTO dto) {
        return episodeService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条集次记录")
    @PutMapping("update")
    @com.yangaobo.security.RequireAdmin
    public boolean update(@Validated @RequestBody EpisodeUpdateDTO dto) {
        return episodeService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条集次记录")
    @DeleteMapping("delete/{id}")
    @com.yangaobo.security.RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return episodeService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除集次记录")
    @DeleteMapping("deleteBatch")
    @com.yangaobo.security.RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return episodeService.deleteBatch(ids);
    }

    @Operation(summary = "修改 - 集次封面", description = "按主键修改集次的封面图片")
    @PostMapping("/uploadVideoCover/{id}")
    @com.yangaobo.security.RequireAdmin
    public Result<String> uploadVideoCover(@RequestParam("videoCoverFile") MultipartFile videoCoverFile, @PathVariable("id") Long id) {
        return new Result<>(episodeService.uploadVideoCover(videoCoverFile, id));
    }

    @Operation(summary = "修改 - 集次视频", description = "按主键修改集次的视频")
    @PostMapping("/uploadVideo/{id}")
    @com.yangaobo.security.RequireAdmin
    public Result<String> uploadVideo(@RequestParam("videoFile") MultipartFile videoFile, @PathVariable("id") Long id) {
        return new Result<>(episodeService.uploadVideo(videoFile, id));
    }

    @Operation(summary = "查询 - 报表打印", description = "打印集次相关的报表数据")
    @GetMapping("excel")
    @com.yangaobo.security.RequireAdmin
    public void excel(HttpServletResponse response) {
        EasyExcelUtil.download(response, "集次统计表", episodeService.getExcelData());
    }

    @Operation(summary = "查询 - 弹幕列表", description = "查询弹幕列表")
    @GetMapping("/searchBarrage/{episodeId}")
    public List<BarrageDoc> searchBarrage(@PathVariable("episodeId") String episodeId) {
        return episodeService.searchBarrage(episodeId);
    }
}
