package com.yangaobo.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yangaobo.entity.RoleMenu;
import com.yangaobo.mapper.RoleMenuMapper;
import com.yangaobo.service.RoleMenuService;
import org.springframework.stereotype.Service;

/**
 * 角色菜单关系表 服务层实现。
 *
 * @author 杨奥博
 * @since v1.0.0
 */
@Service
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu>  implements RoleMenuService{

}
