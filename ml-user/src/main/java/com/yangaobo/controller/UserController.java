package com.yangaobo.controller;

import com.yangaobo.dto.*;
import com.yangaobo.result.Result;
import com.yangaobo.util.EasyExcelUtil;
import com.yangaobo.vo.LoginVO;
import com.yangaobo.vo.PageVO;
import com.yangaobo.vo.UserSimpleListVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.yangaobo.entity.User;
import com.yangaobo.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import com.yangaobo.security.RequireAdmin;
import com.yangaobo.security.SecurityContext;

/**
 * 用户表 控制层。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@RestController
@Tag(name = "用户表接口")
@RequestMapping("/api/v1/user")
public class UserController {
    @Resource
    private UserService userService;

    @Operation(summary = "新增 - 单条新增", description = "新增一条用户记录")
    @PostMapping("insert")
    public boolean insert(@Validated @RequestBody UserInsertDTO dto) {
        return userService.insert(dto);
    }

    @Operation(summary = "查询 - 单条查询", description = "按主键查询一条用户记录")
    @GetMapping("select/{id}")
    public User select(@PathVariable("id") Long id) {
        SecurityContext.requireOwner(id);
        return userService.select(id);
    }

    @Operation(summary = "查询 - 简单列表", description = "查询全部用户记录，仅返回简单信息")
    @GetMapping("simpleList")
    @RequireAdmin
    public List<UserSimpleListVO> simpleList() {
        return userService.simpleList();
    }

    @Operation(summary = "查询 - 分页查询", description = "分页查询用户记录")
    @GetMapping("page")
    @RequireAdmin
    public PageVO<User> page(@Validated UserPageDTO dto) {
        return userService.page(dto);
    }

    @Operation(summary = "修改 - 单条修改", description = "按主键修改一条用户记录")
    @PutMapping("update")
    public boolean update(@Validated @RequestBody UserUpdateDTO dto) {
        SecurityContext.requireOwner(dto.getId());
        return userService.update(dto);
    }

    @Operation(summary = "删除 - 单条删除", description = "按主键删除一条用户记录")
    @DeleteMapping("delete/{id}")
    @RequireAdmin
    public boolean delete(@PathVariable("id") Long id) {
        return userService.delete(id);
    }

    @Operation(summary = "删除 - 批量删除", description = "按主键批量删除用户记录")
    @DeleteMapping("deleteBatch")
    @RequireAdmin
    public boolean deleteBatch(@RequestParam("ids") List<Long> ids) {
        return userService.deleteBatch(ids);
    }

    @Operation(summary = "修改 - 重置密码", description = "按主键重置用户的登录密码为默认密码")
    @PutMapping("resetPassword/{id}")
    @RequireAdmin
    public Result<String> resetPassword(@PathVariable("id") Long id) {
        return new Result<>(userService.resetPassword(id));
    }

    @Operation(summary = "修改 - 登录密码", description = "按主键修改用户的登录密码")
    @PutMapping("updatePassword")
    public boolean updatePassword(@Validated @RequestBody UserUpdatePasswordDTO dto) {
        SecurityContext.requireOwner(dto.getId());
        return userService.updatePassword(dto);
    }

    @Operation(summary = "查询 - 报表打印", description = "打印用户相关的报表数据")
    @GetMapping("excel")
    @RequireAdmin
    public void excel(HttpServletResponse response) {
        EasyExcelUtil.download(response, "用户统计表", userService.getExcelData());
    }

    @Operation(summary = "修改 - 用户头像", description = "按主键修改用户的头像")
    @PostMapping("/uploadAvatar/{id}")
    public Result<String> uploadAvatar(@RequestParam("avatarFile") MultipartFile avatarFile,
                                       @PathVariable("id") Long id) {
        SecurityContext.requireOwner(id);
        return new Result<>(userService.uploadAvatar(avatarFile, id));
    }

    @Operation(summary = "查询 - 解绑验证码", description = "获取旧手机号码的解绑验证码")
    @GetMapping("/getUnboundVcode/{id}")
    public Result<Boolean> getUnboundVcode(@PathVariable("id") Long id, HttpServletRequest request) {
        SecurityContext.requireOwner(id);
        return new Result<>(userService.getUnboundVcode(id, clientIp(request)));
    }

    @Operation(summary = "校验 - 解绑验证码", description = "校验旧手机号码的解绑验证码")
    @GetMapping("/checkUnboundVcode/{id}/{vcode}")
    public boolean checkUnboundVcode(@PathVariable("id") Long id,
                                     @PathVariable("vcode") String vcode) {
        SecurityContext.requireOwner(id);
        return userService.checkUnboundVcode(id, vcode);
    }

    @Operation(summary = "查询 - 绑定验证码", description = "获取新手机号码的绑定验证码")
    @GetMapping("/getBoundVcode/{phone}")
    public Result<Boolean> getBoundVcode(@PathVariable("phone") String phone, HttpServletRequest request) {
        return new Result<>(userService.getBoundVcode(phone, clientIp(request)));
    }

    @Operation(summary = "修改 - 手机号码", description = "修改用户的手机号码")
    @PutMapping("/updatePhone")
    public boolean updatePhone(@Validated @RequestBody UserUpdatePhoneDTO dto) {
        SecurityContext.requireOwner(dto.getId());
        return userService.updatePhone(dto);
    }

    @Operation(summary = "登录 - 账号密码", description = "按账号和密码登录系统")
    @PostMapping("loginByAccount")
    public LoginVO loginByAccount(@Validated @RequestBody LoginByAccountDTO dto) {
        return userService.loginByAccount(dto);
    }

    @Operation(summary = "查询 - 登录验证码", description = "获取手机号码的验证码")
    @GetMapping("/getVcode/{phone}")
    public Result<Boolean> getVcode(@PathVariable("phone") String phone, HttpServletRequest request) {
        return new Result<>(userService.getVcode(phone, clientIp(request)));
    }

    @Operation(summary = "登录 - 手机号码", description = "按手机号码和验证码登录系统")
    @PostMapping("/loginByPhone")
    public LoginVO loginByPhone(@Validated @RequestBody LoginByPhoneDTO dto) {
        return userService.loginByPhone(dto);
    }

    @Operation(summary = "查询 - 统计数据", description = "查询用户相关的统计数据")
    @GetMapping("/statistics")
    @RequireAdmin
    public Map<String, Object> statistics() {
        return userService.statistics();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }
}
