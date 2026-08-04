package com.yangaobo.vo;


import com.yangaobo.entity.Menu;
import com.yangaobo.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author 杨奥博
 */
@Schema(name = "用户登录VO")
@Data
public class LoginVO implements Serializable {
    @Schema(description = "用户信息")
    private User user;
    @Schema(description = "Token令牌")  
    private String token;
    @Schema(description = "角色列表")
    private List<String> roleTitles;
	@Schema(description = "菜单列表")
    private List<Menu> menus;
}
