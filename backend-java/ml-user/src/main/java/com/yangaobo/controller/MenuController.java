package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.MenuInsertDTO;
import com.yangaobo.dto.MenuPageDTO;
import com.yangaobo.dto.MenuUpdateDTO;
import com.yangaobo.vo.MenuSimpleListVO;
import com.yangaobo.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Menu;
import com.yangaobo.service.MenuService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import com.yangaobo.security.RequireAdmin;

/**
 * 菜单表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "菜单表接口")
@RequestMapping("/api/v1/menu")
@RequireAdmin
public class MenuController {

    @Resource
    private MenuService menuService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条菜单记录")
    @PostMapping("insert")
    public boolean insert(@Validated @RequestBody MenuInsertDTO dto) {
        return menuService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条菜单记录")
    @GetMapping("select/{id}")
    public Menu select(@PathVariable("id") Long id) {
        return menuService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部菜单记录，仅返回简单信息")
    @GetMapping("simpleList")
    public List<MenuSimpleListVO> simpleList() {
        return menuService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询菜单记录")
    @GetMapping("page")
    public PageVO<Menu> page(@Validated MenuPageDTO dto) {
        return menuService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条菜单记录")
    @PutMapping("update")
    public boolean update(@Validated @RequestBody MenuUpdateDTO dto) {
        return menuService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条菜单记录")
    @DeleteMapping("delete/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return menuService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除菜单记录")
    @DeleteMapping("deleteBatch")
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return menuService.deleteBatch(ids);
    }

    @Operation(summary = "修改 - 菜单列表", description = "按角色主键修改角色的菜单列表")
    @PutMapping("updateMenusByRoleId")
    public boolean updateMenusByRoleId(@RequestParam("roleId") Long roleId, @RequestParam("menuIds") List<Long> menuIds) {
        return menuService.updateMenusByRoleId(roleId, menuIds);
    }

}
