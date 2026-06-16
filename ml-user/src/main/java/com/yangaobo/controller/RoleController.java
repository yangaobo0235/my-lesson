package com.yangaobo.controller;

import com.mybatisflex.core.paginate.Page;
import com.yangaobo.dto.RoleInsertDTO;
import com.yangaobo.dto.RolePageDTO;
import com.yangaobo.dto.RoleUpdateDTO;
import com.yangaobo.vo.PageVO;
import com.yangaobo.vo.RoleSimpleListVO;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.yangaobo.entity.Role;
import com.yangaobo.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import com.yangaobo.security.RequireAdmin;

/**
 * 角色表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "角色表接口")
@RequestMapping("/api/v1/role")
@RequireAdmin
public class RoleController {

    @Resource
    private RoleService roleService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条角色记录")
    @PostMapping("insert")
    public boolean insert(@Validated @RequestBody RoleInsertDTO dto) {
        return roleService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条角色记录")
    @GetMapping("select/{id}")
    public Role select(@PathVariable("id") Long id) {
        return roleService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部角色记录，仅返回简单信息")
    @GetMapping("simpleList")
    public List<RoleSimpleListVO> simpleList() {
        return roleService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询角色记录")
    @GetMapping("page")
    public PageVO<Role> page(@Validated RolePageDTO dto) {
        return roleService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条角色记录")
    @PutMapping("update")
    public boolean update(@Validated @RequestBody RoleUpdateDTO dto) {
        return roleService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条角色记录")
    @DeleteMapping("delete/{id}")
    public boolean delete(@PathVariable("id") Long id) {
        return roleService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除角色记录")
    @DeleteMapping("deleteBatch")
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return roleService.deleteBatch(ids);
    }

    @Operation(summary = "查询 - 用户角色ID列表", description = "按用户主键查询用户的全部角色ID列表")
    @GetMapping("/listRoleIdsByUserId/{userId}")
    public List<Long> listRoleIdsByUserId(@PathVariable("userId") Long userId) {
        return roleService.listRoleIdsByUserId(userId);
    }

    @Operation(summary = "修改 - 角色列表", description = "按用户主键修改用户的角色列表")
    @PutMapping("/updateRolesByUserId")
    public boolean updateRolesByUserId(@RequestParam("userId") Long userId,
                                       @RequestParam("roleIds") List<Long> roleIds) {
        return roleService.updateRolesByUserId(userId, roleIds);
    }


}
